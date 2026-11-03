#pragma once
#include <filesystem>
#include <string>

// Thin WinHTTP wrapper. Both calls throw std::runtime_error with a plain-language message.
std::string httpGet(const std::string& url);
void httpDownload(const std::string& url, const std::filesystem::path& dest);
