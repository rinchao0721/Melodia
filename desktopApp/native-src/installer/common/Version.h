#pragma once

#include <optional>
#include <string>

namespace ver {

// 完整版本：主.次.补丁 加可选的 -beta.N / -rc.N；sub 与 VersionCodeResolver 的子码一致
struct Release {
    unsigned long major = 0;
    unsigned long minor = 0;
    unsigned long patch = 0;
    int sub = 99;
};

// 格式不符时返回空
std::optional<Release> Parse(const std::wstring& text);

// 返回 <0 / 0 / >0，beta < rc < 正式
int Compare(const Release& a, const Release& b);

}  // namespace ver
