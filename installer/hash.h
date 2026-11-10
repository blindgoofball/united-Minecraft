#pragma once
#include <filesystem>
#include <string>

// Lowercase hex digest of a whole file. algorithm is "sha256" or "sha512".
std::string fileDigest(const std::filesystem::path& file, const std::string& algorithm);
