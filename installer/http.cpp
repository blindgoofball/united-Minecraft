#include "http.h"

#include <windows.h>
#include <winhttp.h>

#include <fstream>
#include <memory>
#include <stdexcept>

namespace {

std::wstring widen(const std::string& s) {
    if (s.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int) s.size(), nullptr, 0);
    std::wstring w(n, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int) s.size(), w.data(), n);
    return w;
}

struct HandleCloser {
    void operator()(void* h) const { if (h) WinHttpCloseHandle(h); }
};
using Handle = std::unique_ptr<void, HandleCloser>;

// Opens the request and returns it with the response headers received and status checked.
Handle open(const std::string& url, Handle& session, Handle& connection) {
    std::wstring wurl = widen(url);
    URL_COMPONENTS parts{};
    parts.dwStructSize = sizeof(parts);
    parts.dwSchemeLength = parts.dwHostNameLength = parts.dwUrlPathLength = parts.dwExtraInfoLength = (DWORD) -1;
    if (!WinHttpCrackUrl(wurl.c_str(), 0, 0, &parts))
        throw std::runtime_error("Not a valid web address: " + url);
    std::wstring host(parts.lpszHostName, parts.dwHostNameLength);
    std::wstring path(parts.lpszUrlPath, parts.dwUrlPathLength + parts.dwExtraInfoLength);

    session.reset(WinHttpOpen(L"united-minecraft-installer/0.1", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                              WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0));
    if (!session) throw std::runtime_error("Could not start the network library.");
    WinHttpSetTimeouts(session.get(), 15000, 15000, 30000, 60000);
    DWORD redirects = WINHTTP_OPTION_REDIRECT_POLICY_DISALLOW_HTTPS_TO_HTTP;
    WinHttpSetOption(session.get(), WINHTTP_OPTION_REDIRECT_POLICY, &redirects, sizeof(redirects));

    connection.reset(WinHttpConnect(session.get(), host.c_str(), parts.nPort, 0));
    if (!connection) throw std::runtime_error("Could not connect to " + url);

    DWORD flags = parts.nScheme == INTERNET_SCHEME_HTTPS ? WINHTTP_FLAG_SECURE : 0;
    Handle request(WinHttpOpenRequest(connection.get(), L"GET", path.c_str(), nullptr, WINHTTP_NO_REFERER,
                                      WINHTTP_DEFAULT_ACCEPT_TYPES, flags));
    if (!request) throw std::runtime_error("Could not create a request for " + url);

    if (!WinHttpSendRequest(request.get(), WINHTTP_NO_ADDITIONAL_HEADERS, 0, WINHTTP_NO_REQUEST_DATA, 0, 0, 0) ||
        !WinHttpReceiveResponse(request.get(), nullptr))
        throw std::runtime_error("Could not reach " + url + ". Check your internet connection (error " +
                                 std::to_string(GetLastError()) + ").");

    DWORD status = 0, size = sizeof(status);
    WinHttpQueryHeaders(request.get(), WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                        WINHTTP_HEADER_NAME_BY_INDEX, &status, &size, WINHTTP_NO_HEADER_INDEX);
    if (status != 200)
        throw std::runtime_error("The server answered " + std::to_string(status) + " for " + url);
    return request;
}

template <typename Sink>
void pump(HINTERNET request, Sink&& sink) {
    char buffer[64 * 1024];
    for (;;) {
        DWORD got = 0;
        if (!WinHttpReadData(request, buffer, sizeof(buffer), &got))
            throw std::runtime_error("The download was interrupted (error " + std::to_string(GetLastError()) + ").");
        if (got == 0) return;
        sink(buffer, got);
    }
}

} // namespace

std::string httpGet(const std::string& url) {
    Handle session, connection;
    Handle request = open(url, session, connection);
    std::string body;
    pump(request.get(), [&](const char* data, DWORD n) { body.append(data, n); });
    return body;
}

void httpDownload(const std::string& url, const std::filesystem::path& dest) {
    Handle session, connection;
    Handle request = open(url, session, connection);
    std::ofstream out(dest, std::ios::binary | std::ios::trunc);
    if (!out) throw std::runtime_error("Could not write " + dest.string());
    pump(request.get(), [&](const char* data, DWORD n) { out.write(data, n); });
    out.close();
    if (!out) throw std::runtime_error("Could not finish writing " + dest.string());
}
