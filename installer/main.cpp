// United Minecraft installer: installs or updates Fabric Loader, Fabric API and the United
// Minecraft mod for the official Minecraft launcher. Console only, plain lines of text, so a
// screen reader reads everything in order.

#include <windows.h>
#include <tlhelp32.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "hash.h"
#include "http.h"
#include "miniz.h"
#include "third_party/json.hpp"

namespace fs = std::filesystem;
using json = nlohmann::json;
using ordered = nlohmann::ordered_json;

namespace {

const char* kReleasesUrl = "https://api.github.com/repos/blindgoofball/united-Minecraft/releases?per_page=100";
const char* kProfileKey = "united-minecraft";
const char* kModId = "united_minecraft";
const char* kFabricApiId = "fabric-api";

struct Options {
    fs::path gameDir;
    std::string mc;
    std::string launcher;   // "win32" or "microsoft_store"; empty means detect
    bool dryRun = false;
    bool yes = false;
};

using Version = std::vector<int>;

// "0.19.5", "1.8.0+mc26.3" and "26.3-pre-1" all parse; anything after the first non-numeric
// character is ignored.
Version parseVersion(const std::string& s) {
    Version v;
    size_t i = 0;
    while (i < s.size()) {
        if (!isdigit((unsigned char) s[i])) break;
        int n = 0;
        while (i < s.size() && isdigit((unsigned char) s[i])) n = n * 10 + (s[i++] - '0');
        v.push_back(n);
        if (i < s.size() && s[i] == '.') i++; else break;
    }
    return v;
}

bool atLeast(const Version& a, const Version& b) {
    return !std::lexicographical_compare(a.begin(), a.end(), b.begin(), b.end());
}

std::string utf8(const fs::path& p) {
    auto s = p.u8string();
    return std::string(s.begin(), s.end());
}

std::string readFile(const fs::path& p) {
    std::ifstream in(p, std::ios::binary);
    if (!in) throw std::runtime_error("Could not read " + utf8(p));
    std::ostringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

void writeFileAtomic(const fs::path& p, const std::string& text) {
    fs::path tmp = p;
    tmp += ".tmp";
    {
        std::ofstream out(tmp, std::ios::binary | std::ios::trunc);
        out << text;
        if (!out) throw std::runtime_error("Could not write " + utf8(tmp));
    }
    fs::rename(tmp, p);
}

std::string nowIso() {
    auto t = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    tm utc{};
    gmtime_s(&utc, &t);
    char buf[40];
    strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%S.000Z", &utc);
    return buf;
}

std::string urlEncode(const std::string& s) {
    static const char* hex = "0123456789ABCDEF";
    std::string out;
    for (unsigned char c : s) {
        if (isalnum(c) || c == '-' || c == '.' || c == '_' || c == '~') out += (char) c;
        else { out += '%'; out += hex[c >> 4]; out += hex[c & 15]; }
    }
    return out;
}

// Reads a text entry out of a jar, or returns nothing if it is not a readable zip or lacks it.
std::optional<std::string> readJarEntry(const fs::path& jar, const char* entry) {
    std::string bytes;
    try { bytes = readFile(jar); } catch (...) { return std::nullopt; }
    mz_zip_archive zip{};
    if (!mz_zip_reader_init_mem(&zip, bytes.data(), bytes.size(), 0)) return std::nullopt;
    size_t size = 0;
    void* data = mz_zip_reader_extract_file_to_heap(&zip, entry, &size, 0);
    mz_zip_reader_end(&zip);
    if (!data) return std::nullopt;
    std::string out((const char*) data, size);
    mz_free(data);
    return out;
}

std::optional<json> readModJson(const fs::path& jar) {
    auto text = readJarEntry(jar, "fabric.mod.json");
    if (!text) return std::nullopt;
    auto parsed = json::parse(*text, nullptr, false);
    if (parsed.is_discarded()) return std::nullopt;
    return parsed;
}

fs::path defaultGameDir() {
    const wchar_t* appdata = _wgetenv(L"APPDATA");
    if (!appdata) throw std::runtime_error("The APPDATA folder is not set, so use --game-dir.");
    return fs::path(appdata) / L".minecraft";
}

void say(const std::string& line) { std::cout << line << "\n" << std::flush; }

// ---- What to install -------------------------------------------------------------------

struct ModRelease {
    std::string version;   // 1.8.0
    std::string mc;        // 26.3
    std::string fileName;
    std::string url;
    std::string sha256;
};

ModRelease pickModRelease(const std::string& wantedMc) {
    json releases = json::parse(httpGet(kReleasesUrl));
    std::optional<ModRelease> best;
    for (auto& r : releases) {
        if (r.value("draft", false) || r.value("prerelease", false)) continue;
        std::string tag = r.value("tag_name", "");
        size_t plus = tag.find("+mc");
        if (tag.empty() || tag[0] != 'v' || plus == std::string::npos) continue;
        ModRelease c;
        c.version = tag.substr(1, plus - 1);
        c.mc = tag.substr(plus + 3);
        if (!wantedMc.empty() && c.mc != wantedMc) continue;
        for (auto& a : r["assets"]) {
            std::string name = a.value("name", "");
            if (name.size() > 11 && name.compare(name.size() - 11, 11, "-fabric.jar") == 0) {
                c.fileName = name;
                c.url = a.value("browser_download_url", "");
                std::string digest = a.value("digest", "");
                if (digest.rfind("sha256:", 0) == 0) c.sha256 = digest.substr(7);
            }
        }
        if (c.url.empty()) continue;
        // The list is newest first, so the first match wins unless a later one is a higher
        // version of the same Minecraft version.
        if (!best) best = c;
        else if (c.mc == best->mc && !atLeast(parseVersion(best->version), parseVersion(c.version))) best = c;
        if (!wantedMc.empty() && best) continue;
    }
    if (!best)
        throw std::runtime_error(wantedMc.empty() ? "No United Minecraft release was found."
                                                  : "No United Minecraft release supports Minecraft " + wantedMc + ".");
    return *best;
}

struct FabricApiFile {
    std::string version, fileName, url, sha512;
};

FabricApiFile pickFabricApi(const std::string& mc) {
    std::string url = "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=" +
                      urlEncode("[\"" + mc + "\"]") + "&loaders=" + urlEncode("[\"fabric\"]");
    json versions = json::parse(httpGet(url));
    const json* chosen = nullptr;
    for (auto& v : versions) {
        if (v.value("version_type", "") == "release") { chosen = &v; break; }
    }
    if (!chosen && !versions.empty()) chosen = &versions[0];
    if (!chosen) throw std::runtime_error("Fabric API is not available for Minecraft " + mc + " yet.");
    for (auto& f : (*chosen)["files"]) {
        if (!f.value("primary", false)) continue;
        return {chosen->value("version_number", ""), f.value("filename", ""), f.value("url", ""),
                f["hashes"].value("sha512", "")};
    }
    throw std::runtime_error("Fabric API has no downloadable file for Minecraft " + mc + ".");
}

// ---- Fabric Loader ---------------------------------------------------------------------

struct LoaderChoice {
    std::string version;
    bool installedAlready = false;
};

// Keeps the Fabric Loader already installed for this Minecraft version when it is new enough,
// otherwise picks the newest stable one.
LoaderChoice pickLoader(const fs::path& gameDir, const std::string& mc, const Version& minimum) {
    std::optional<std::string> best;
    fs::path versions = gameDir / "versions";
    std::string suffix = "-" + mc;
    if (fs::exists(versions)) {
        for (auto& e : fs::directory_iterator(versions)) {
            std::string name = utf8(e.path().filename());
            const std::string prefix = "fabric-loader-";
            if (name.rfind(prefix, 0) != 0 || name.size() <= prefix.size() + suffix.size() ||
                name.compare(name.size() - suffix.size(), suffix.size(), suffix) != 0)
                continue;
            if (!fs::exists(e.path() / (name + ".json"))) continue;
            std::string loader = name.substr(prefix.size(), name.size() - prefix.size() - suffix.size());
            if (!atLeast(parseVersion(loader), minimum)) continue;
            if (!best || atLeast(parseVersion(loader), parseVersion(*best))) best = loader;
        }
    }
    if (best) return {*best, true};

    json loaders = json::parse(httpGet("https://meta.fabricmc.net/v2/versions/loader/" + urlEncode(mc)));
    for (auto& l : loaders) {
        if (l["loader"].value("stable", false)) {
            std::string v = l["loader"].value("version", "");
            if (!atLeast(parseVersion(v), minimum))
                throw std::runtime_error("The newest Fabric Loader (" + v + ") is older than this mod needs.");
            return {v, false};
        }
    }
    throw std::runtime_error("Fabric does not support Minecraft " + mc + " yet.");
}

void installLoader(const fs::path& gameDir, const std::string& mc, const std::string& loader) {
    std::string id = "fabric-loader-" + loader + "-" + mc;
    std::string profileJson = httpGet("https://meta.fabricmc.net/v2/versions/loader/" + urlEncode(mc) + "/" +
                                      urlEncode(loader) + "/profile/json");
    if (json::parse(profileJson, nullptr, false).is_discarded())
        throw std::runtime_error("Fabric sent back a version file that could not be read.");
    fs::path dir = gameDir / "versions" / id;
    fs::create_directories(dir);
    std::error_code ec;
    fs::remove(dir / (id + ".jar"), ec);   // a stale jar from an older install, as the Fabric installer does
    writeFileAtomic(dir / (id + ".json"), profileJson);

    // The launcher would download these on first launch anyway. Doing it here, as the Fabric
    // installer does, shows a connection problem now. A failure is only a warning for that reason.
    json profile = json::parse(profileJson);
    for (auto& lib : profile["libraries"]) {
        std::string name = lib.value("name", ""), base = lib.value("url", "");
        size_t a = name.find(':'), b = a == std::string::npos ? a : name.find(':', a + 1);
        if (b == std::string::npos || base.empty()) continue;
        std::string group = name.substr(0, a), artifact = name.substr(a + 1, b - a - 1), ver = name.substr(b + 1);
        std::string rel = group, file = artifact + "-" + ver + ".jar";
        std::replace(rel.begin(), rel.end(), '.', '/');
        rel += "/" + artifact + "/" + ver + "/" + file;
        std::string localRel = rel;
        std::replace(localRel.begin(), localRel.end(), ' ', '_');
        fs::path dest = gameDir / "libraries" / fs::path(localRel);
        std::string sha256 = lib.value("sha256", "");
        if (fs::exists(dest) && (sha256.empty() || fileDigest(dest, "sha256") == sha256)) continue;
        try {
            fs::create_directories(dest.parent_path());
            fs::path part = dest;
            part += ".part";
            httpDownload(base + rel, part);
            if (!sha256.empty() && fileDigest(part, "sha256") != sha256)
                throw std::runtime_error("checksum does not match");
            fs::rename(part, dest);
        } catch (const std::exception& e) {
            say("Warning: could not download " + name + " (" + e.what() + "). The launcher will try again when you play.");
        }
    }
}

// "fabric-loader-0.19.5-26.3" -> loader "0.19.5", mc "26.3". Loader versions never contain a dash.
bool splitFabricVersionId(const std::string& id, std::string& loader, std::string& mc) {
    const std::string prefix = "fabric-loader-";
    if (id.rfind(prefix, 0) != 0) return false;
    size_t dash = id.find('-', prefix.size());
    if (dash == std::string::npos) return false;
    loader = id.substr(prefix.size(), dash - prefix.size());
    mc = id.substr(dash + 1);
    return true;
}

struct ExistingProfile {
    std::string key, name, versionId;
};

// The official launcher keeps its profiles in one of two files; the Microsoft Store build uses the second.
struct LauncherFile {
    const char* option;
    const char* fileName;
    const char* label;
};
const LauncherFile kLauncherFiles[] = {
    {"win32", "launcher_profiles.json", "the standard Minecraft launcher"},
    {"microsoft_store", "launcher_profiles_microsoft_store.json", "the Microsoft Store Minecraft launcher"},
};

ordered readLauncherProfiles(const fs::path& gameDir, const std::string& fileName) {
    return ordered::parse(readFile(gameDir / fileName));
}

bool launcherRunning() {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return false;
    PROCESSENTRY32W entry{};
    entry.dwSize = sizeof(entry);
    bool found = false;
    for (BOOL ok = Process32FirstW(snap, &entry); ok && !found; ok = Process32NextW(snap, &entry))
        found = _wcsicmp(entry.szExeFile, L"MinecraftLauncher.exe") == 0;
    CloseHandle(snap);
    return found;
}

// Fabric profiles the player already has for this Minecraft version, not counting ours.
std::vector<ExistingProfile> findFabricProfiles(const ordered& root, const std::string& mc) {
    std::vector<ExistingProfile> found;
    if (!root.contains("profiles") || !root["profiles"].is_object()) return found;
    for (auto& [key, p] : root["profiles"].items()) {
        if (key == kProfileKey || !p.is_object()) continue;
        std::string id = p.value("lastVersionId", ""), loader, profileMc;
        if (!splitFabricVersionId(id, loader, profileMc) || profileMc != mc) continue;
        std::string name = p.value("name", "");
        found.push_back({key, name.empty() ? key : name, id});
    }
    return found;
}

// Which launcher profile an earlier run chose, kept so updates keep using it without asking again.
fs::path statePath(const fs::path& gameDir) { return gameDir / "united-installer.json"; }

// Remembered per launcher file, since each file has its own profiles.
std::string rememberedProfile(const fs::path& gameDir, const std::string& launcherFile) {
    if (!fs::exists(statePath(gameDir))) return {};
    auto state = json::parse(readFile(statePath(gameDir)), nullptr, false);
    if (!state.is_object() || !state.contains("profiles") || !state["profiles"].is_object()) return {};
    return state["profiles"].value(launcherFile, "");
}

void rememberProfile(const fs::path& gameDir, const std::string& launcherFile, const std::string& key) {
    json state = json::object();
    if (fs::exists(statePath(gameDir))) {
        auto old = json::parse(readFile(statePath(gameDir)), nullptr, false);
        if (old.is_object()) state = old;
    }
    state["profiles"][launcherFile] = key;
    writeFileAtomic(statePath(gameDir), state.dump(2));
}

// Points one launcher profile at the Fabric Loader, creating our own profile if the key is new
// and leaving everything else in the file as it was. A profile the player made themselves
// (adopted) keeps its loader when that loader is already new enough, and keeps its last-used time.
void setLauncherProfile(const fs::path& gameDir, const std::string& launcherFile, const std::string& key,
                        const std::string& versionId, bool adopted, const Version& minimumLoader) {
    fs::path file = gameDir / launcherFile;
    ordered root = readLauncherProfiles(gameDir, launcherFile);
    if (!root.contains("profiles") || !root["profiles"].is_object()) root["profiles"] = ordered::object();
    ordered& profiles = root["profiles"];
    std::string now = nowIso();
    if (!profiles.contains(key)) {
        profiles[key] = {{"created", now}, {"icon", "Grass"}, {"name", "United Minecraft"}, {"type", "custom"}};
    }
    ordered& profile = profiles[key];
    std::string loader, mc, wantLoader, wantMc;
    bool keep = adopted && splitFabricVersionId(profile.value("lastVersionId", ""), loader, mc) &&
                splitFabricVersionId(versionId, wantLoader, wantMc) && mc == wantMc &&
                atLeast(parseVersion(loader), minimumLoader);
    if (!keep) profile["lastVersionId"] = versionId;
    if (!adopted) profile["lastUsed"] = now;

    fs::path backup = file;
    backup += ".before-united-installer";
    if (!fs::exists(backup)) fs::copy_file(file, backup);
    writeFileAtomic(file, root.dump(2));
}

// ---- The mods folder -------------------------------------------------------------------

struct ModsPlan {
    std::vector<fs::path> remove;
    bool fabricApiCurrent = false;
    bool modCurrent = false;
};

ModsPlan planMods(const fs::path& modsDir, const std::string& apiFile, const std::string& modFile) {
    ModsPlan plan;
    if (!fs::exists(modsDir)) return plan;
    for (auto& e : fs::directory_iterator(modsDir)) {
        if (!e.is_regular_file() || e.path().extension() != ".jar") continue;
        std::string name = utf8(e.path().filename());
        auto mod = readModJson(e.path());
        if (!mod) {
            // A jar we cannot read is most likely open in a running game. Leaving a stale copy of
            // our own files beside the new ones would crash the game, so queue it; the swap then
            // fails with a clear message instead.
            if (name.rfind("united-minecraft", 0) == 0 || name.rfind("fabric-api", 0) == 0)
                plan.remove.push_back(e.path());
            continue;
        }
        std::string id = mod->value("id", "");
        if (id == kFabricApiId) {
            if (name == apiFile) plan.fabricApiCurrent = true; else plan.remove.push_back(e.path());
        } else if (id == kModId) {
            if (name == modFile) plan.modCurrent = true; else plan.remove.push_back(e.path());
        }
    }
    return plan;
}

void download(const std::string& label, const std::string& url, const fs::path& dest,
              const std::string& algorithm, const std::string& expected) {
    say("Downloading " + label + "...");
    httpDownload(url, dest);
    if (!expected.empty() && fileDigest(dest, algorithm) != expected)
        throw std::runtime_error(label + " did not download correctly (its checksum does not match). Nothing was changed.");
}

// Moves the old files aside first (which fails cleanly if Minecraft has them open), puts the new
// ones in, then deletes the old. A failure at any point puts the old files back.
void swapMods(const fs::path& modsDir, const std::vector<fs::path>& remove,
              const std::vector<std::pair<fs::path, fs::path>>& add) {
    std::vector<std::pair<fs::path, fs::path>> parked;
    std::vector<fs::path> placed;
    try {
        for (auto& old : remove) {
            fs::path aside = old;
            aside += ".old-united-installer";
            try { fs::rename(old, aside); }
            catch (...) { throw std::runtime_error("Could not replace " + utf8(old.filename()) + ". Close Minecraft and try again."); }
            parked.push_back({old, aside});
        }
        fs::create_directories(modsDir);
        for (auto& [from, to] : add) {
            fs::copy_file(from, to, fs::copy_options::overwrite_existing);
            placed.push_back(to);
        }
    } catch (...) {
        for (auto& p : placed) { std::error_code ec; fs::remove(p, ec); }
        for (auto& [orig, aside] : parked) { std::error_code ec; fs::rename(aside, orig, ec); }
        throw;
    }
    for (auto& [orig, aside] : parked) { std::error_code ec; fs::remove(aside, ec); }
}

// ---- Main flow -------------------------------------------------------------------------

int run(const Options& opt) {
    fs::path gameDir = opt.gameDir.empty() ? defaultGameDir() : opt.gameDir;
    say("Minecraft folder: " + utf8(gameDir));

    // Which launcher's profile file to edit. Like the Fabric installer: use the only one that
    // exists, and ask when both do.
    std::vector<const LauncherFile*> present;
    for (auto& f : kLauncherFiles)
        if (fs::exists(gameDir / f.fileName) && (opt.launcher.empty() || opt.launcher == f.option)) present.push_back(&f);
    if (present.empty())
        throw std::runtime_error("No Minecraft launcher data found in " + utf8(gameDir) +
                                 ". Open the Minecraft launcher once, then run this again.");
    const LauncherFile* launcher = present[0];
    if (present.size() > 1) {
        if (opt.yes || opt.dryRun) {
            if (opt.dryRun) say("Both launchers were found; you will be asked which to set up.");
            else throw std::runtime_error("Both the standard and the Microsoft Store launcher were found. Use --launcher win32 or --launcher microsoft_store.");
        } else {
            say("Both launchers were found. Which one do you play with? Type a number and press Enter, or N to cancel.");
            for (size_t i = 0; i < present.size(); i++)
                say("  " + std::to_string(i + 1) + ": " + present[i]->label);
            std::string answer;
            std::getline(std::cin, answer);
            size_t pick = (size_t) atoi(answer.c_str());
            if (pick < 1 || pick > present.size()) { say("Nothing was changed."); return 0; }
            launcher = present[pick - 1];
        }
    }
    say(std::string("Setting up ") + launcher->label + ".");
    if (!opt.dryRun && launcherRunning())
        say("Warning: the Minecraft launcher is open. Close it before continuing, or it may undo the profile change.");

    say("Looking up the latest United Minecraft release...");
    ModRelease mod = pickModRelease(opt.mc);
    say("Latest: United Minecraft " + mod.version + " for Minecraft " + mod.mc + ".");

    fs::path staging = fs::temp_directory_path() / "united-minecraft-installer";
    fs::remove_all(staging);
    fs::create_directories(staging);

    fs::path stagedMod = staging / mod.fileName;
    download("United Minecraft", mod.url, stagedMod, "sha256", mod.sha256);

    auto modJson = readModJson(stagedMod);
    if (!modJson) throw std::runtime_error("The downloaded mod file could not be read.");
    Version minimumLoader;
    if (modJson->contains("depends") && (*modJson)["depends"].contains("fabricloader")) {
        std::string req = (*modJson)["depends"]["fabricloader"].get<std::string>();
        if (req.rfind(">=", 0) == 0) minimumLoader = parseVersion(req.substr(2));
    }

    say("Looking up Fabric API and Fabric Loader...");
    FabricApiFile api = pickFabricApi(mod.mc);
    LoaderChoice loader = pickLoader(gameDir, mod.mc, minimumLoader);
    fs::path modsDir = gameDir / "mods";
    ModsPlan plan = planMods(modsDir, api.fileName, mod.fileName);

    say("");
    say("Plan:");
    say(std::string("  Fabric Loader ") + loader.version + (loader.installedAlready ? " (already installed, kept)" : " (will be installed)"));
    say(std::string("  Fabric API ") + api.version + (plan.fabricApiCurrent ? " (already up to date)" : " (will be installed)"));
    say(std::string("  United Minecraft ") + mod.version + (plan.modCurrent ? " (already up to date)" : " (will be installed)"));
    for (auto& r : plan.remove) say("  Will remove old file: " + utf8(r.filename()));

    // Which launcher profile to use: the one a past run chose, else ours, else ask when the player
    // already has a Fabric profile for this Minecraft version.
    ordered launcherData = readLauncherProfiles(gameDir, launcher->fileName);
    const ordered empty = ordered::object();
    const ordered& known = launcherData.contains("profiles") ? launcherData["profiles"] : empty;
    std::string remembered = rememberedProfile(gameDir, launcher->fileName);
    std::string profileKey;
    if (!remembered.empty() && known.contains(remembered)) profileKey = remembered;
    else if (known.contains(kProfileKey)) profileKey = kProfileKey;
    std::vector<ExistingProfile> existing;
    if (profileKey.empty()) existing = findFabricProfiles(launcherData, mod.mc);

    std::string profileName = "United Minecraft";
    if (!profileKey.empty()) {
        std::string name = known[profileKey].value("name", "");
        if (!name.empty()) profileName = name;
        std::string oldLoader, oldMc;
        if (splitFabricVersionId(known[profileKey].value("lastVersionId", ""), oldLoader, oldMc) && oldMc != mod.mc)
            say("  Profile \"" + profileName + "\" will move from Minecraft " + oldMc + " to Minecraft " + mod.mc + ".");
        say("  Launcher profile \"" + profileName + "\" will use Fabric Loader " + loader.version + " for Minecraft " + mod.mc + ".");
    } else if (existing.empty()) {
        say("  A launcher profile \"United Minecraft\" will be created for Minecraft " + mod.mc + ".");
    } else {
        say("  You already have a Fabric profile for Minecraft " + mod.mc + ", so you will be asked how to set up the launcher.");
    }

    if (opt.dryRun) { say("Dry run, so nothing was changed."); fs::remove_all(staging); return 0; }

    bool adopted = false;
    if (!opt.yes) {
        if (existing.empty()) {
            say("Press Enter to continue, or type N and press Enter to cancel.");
        } else {
            say("");
            say("Which launcher profile should be used? Type a number and press Enter, or N to cancel.");
            for (size_t i = 0; i < existing.size(); i++)
                say("  " + std::to_string(i + 1) + ": Use your existing profile \"" + existing[i].name + "\"");
            say("  " + std::to_string(existing.size() + 1) + ": Create a separate \"United Minecraft\" profile and leave yours alone");
        }
        std::string answer;
        std::getline(std::cin, answer);
        if (!answer.empty() && (answer[0] == 'n' || answer[0] == 'N')) { say("Cancelled. Nothing was changed."); return 0; }
        if (!existing.empty()) {
            size_t pick = (size_t) atoi(answer.c_str());
            if (pick < 1 || pick > existing.size() + 1) { say("That was not one of the choices. Nothing was changed."); return 0; }
            if (pick <= existing.size()) { profileKey = existing[pick - 1].key; profileName = existing[pick - 1].name; }
        }
    }
    if (profileKey.empty()) profileKey = kProfileKey;
    adopted = profileKey != kProfileKey;

    fs::create_directories(modsDir);   // the Fabric installer creates it alongside the profile
    std::vector<std::pair<fs::path, fs::path>> add;
    if (!plan.fabricApiCurrent) {
        fs::path staged = staging / api.fileName;
        download("Fabric API", api.url, staged, "sha512", api.sha512);
        add.push_back({staged, modsDir / api.fileName});
    }
    if (!plan.modCurrent) add.push_back({stagedMod, modsDir / mod.fileName});

    // Everything is downloaded and checked before anything changes. Adding the loader only creates
    // a new folder, so it goes first: if it fails, the mods are still as the player left them.
    if (!loader.installedAlready) {
        say("Installing Fabric Loader " + loader.version + "...");
        installLoader(gameDir, mod.mc, loader.version);
    }
    swapMods(modsDir, plan.remove, add);
    setLauncherProfile(gameDir, launcher->fileName, profileKey, "fabric-loader-" + loader.version + "-" + mod.mc, adopted, minimumLoader);
    rememberProfile(gameDir, launcher->fileName, profileKey);
    fs::remove_all(staging);

    say("");
    say("Done. Open the Minecraft launcher, choose the \"" + profileName + "\" profile, and press Play.");
    say("The first launch downloads Fabric's files, so it can take a moment.");
    return 0;
}

Options parseArgs(int argc, wchar_t** argv) {
    Options opt;
    for (int i = 1; i < argc; i++) {
        std::wstring a = argv[i];
        if (a == L"--dry-run") opt.dryRun = true;
        else if (a == L"--yes") opt.yes = true;
        else if (a == L"--launcher" && i + 1 < argc) {
            std::wstring w = argv[++i];
            opt.launcher.assign(w.begin(), w.end());
        }
        else if (a == L"--game-dir" && i + 1 < argc) opt.gameDir = argv[++i];
        else if (a == L"--mc" && i + 1 < argc) {
            std::wstring w = argv[++i];
            opt.mc.assign(w.begin(), w.end());
        } else
            throw std::runtime_error("Unknown option. Options: --dry-run, --yes, --mc <version>, --game-dir <folder>, --launcher <win32|microsoft_store>.");
    }
    return opt;
}

} // namespace

int wmain(int argc, wchar_t** argv) {
    SetConsoleOutputCP(CP_UTF8);
    try {
        return run(parseArgs(argc, argv));
    } catch (const std::exception& e) {
        std::cerr << "Problem: " << e.what() << "\n";
        return 1;
    }
}
