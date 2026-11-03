#include "hash.h"

#include <windows.h>
#include <bcrypt.h>

#include <fstream>
#include <stdexcept>
#include <vector>

std::string fileDigest(const std::filesystem::path& file, const std::string& algorithm) {
    LPCWSTR id = algorithm == "sha512" ? BCRYPT_SHA512_ALGORITHM : BCRYPT_SHA256_ALGORITHM;
    BCRYPT_ALG_HANDLE alg = nullptr;
    if (BCryptOpenAlgorithmProvider(&alg, id, nullptr, 0) != 0)
        throw std::runtime_error("Could not start the hashing library.");

    DWORD size = 0, got = 0;
    BCryptGetProperty(alg, BCRYPT_HASH_LENGTH, (PUCHAR) &size, sizeof(size), &got, 0);
    BCRYPT_HASH_HANDLE hash = nullptr;
    if (BCryptCreateHash(alg, &hash, nullptr, 0, nullptr, 0, 0) != 0) {
        BCryptCloseAlgorithmProvider(alg, 0);
        throw std::runtime_error("Could not start the hashing library.");
    }

    std::ifstream in(file, std::ios::binary);
    std::vector<char> buffer(64 * 1024);
    while (in) {
        in.read(buffer.data(), (std::streamsize) buffer.size());
        std::streamsize n = in.gcount();
        if (n > 0) BCryptHashData(hash, (PUCHAR) buffer.data(), (ULONG) n, 0);
    }

    std::vector<unsigned char> digest(size);
    BCryptFinishHash(hash, digest.data(), size, 0);
    BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(alg, 0);

    static const char* hex = "0123456789abcdef";
    std::string out;
    for (unsigned char b : digest) {
        out += hex[b >> 4];
        out += hex[b & 15];
    }
    return out;
}
