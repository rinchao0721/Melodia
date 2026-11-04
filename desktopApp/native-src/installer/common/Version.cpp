#include "Version.h"

#include "Sys.h"

#include <vector>

namespace ver {

namespace {

constexpr int kBetaMax = 49;
constexpr int kRcMax = 48;
constexpr size_t kMaxDigits = 6;

std::optional<unsigned long> ParseNumber(const std::wstring& s) {
    if (s.empty() || s.size() > kMaxDigits) return std::nullopt;
    unsigned long value = 0;
    for (wchar_t c : s) {
        if (c < L'0' || c > L'9') return std::nullopt;
        value = value * 10 + static_cast<unsigned long>(c - L'0');
    }
    return value;
}

std::vector<std::wstring> Split(const std::wstring& s, wchar_t sep) {
    std::vector<std::wstring> parts;
    size_t pos = 0;
    while (true) {
        size_t end = s.find(sep, pos);
        parts.push_back(s.substr(pos, end == std::wstring::npos ? std::wstring::npos : end - pos));
        if (end == std::wstring::npos) break;
        pos = end + 1;
    }
    return parts;
}

}  // namespace

std::optional<Release> Parse(const std::wstring& text) {
    std::wstring s = sys::Trim(text);
    if (!s.empty() && (s[0] == L'v' || s[0] == L'V')) s.erase(0, 1);

    size_t dash = s.find(L'-');
    std::vector<std::wstring> base = Split(s.substr(0, dash), L'.');
    if (base.size() != 3) return std::nullopt;

    Release r;
    unsigned long* fields[] = {&r.major, &r.minor, &r.patch};
    for (size_t i = 0; i < 3; ++i) {
        std::optional<unsigned long> n = ParseNumber(base[i]);
        if (!n) return std::nullopt;
        *fields[i] = *n;
    }
    if (dash == std::wstring::npos) return r;

    std::vector<std::wstring> suffix = Split(s.substr(dash + 1), L'.');
    if (suffix.size() != 2) return std::nullopt;
    std::optional<unsigned long> num = ParseNumber(suffix[1]);
    if (!num) return std::nullopt;
    if (suffix[0] == L"beta" && *num <= static_cast<unsigned long>(kBetaMax)) {
        r.sub = static_cast<int>(*num);
    } else if (suffix[0] == L"rc" && *num <= static_cast<unsigned long>(kRcMax)) {
        r.sub = 50 + static_cast<int>(*num);
    } else {
        return std::nullopt;
    }
    return r;
}

int Compare(const Release& a, const Release& b) {
    const unsigned long av[] = {a.major, a.minor, a.patch, static_cast<unsigned long>(a.sub)};
    const unsigned long bv[] = {b.major, b.minor, b.patch, static_cast<unsigned long>(b.sub)};
    for (size_t i = 0; i < 4; ++i) {
        if (av[i] != bv[i]) return av[i] < bv[i] ? -1 : 1;
    }
    return 0;
}

}  // namespace ver
