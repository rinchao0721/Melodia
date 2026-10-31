# 网易云播放直链的客户端身份与音质语义

2026-10-31 实测。接口为 **明文通道** `POST https://interface3.music.163.com/api/song/enhance/player/url/v1`
（`application/x-www-form-urlencoded`，`Cookie` 承载身份，非 eapi/weapi 加密通道）。

## 结论

1. **`os` / `appver` 是「能不能拿到直链」的闸门，不是音质旋钮。**
   两者都缺时，即使外层 `code` 仍是 `200`，`data[0].url` 也为 `null`；补上后才给 VIP 曲目的 128k 试听片段。
2. **音质档位只由登录态（`MUSIC_U`）决定。**
   同一 VIP 账号下，`os=pc; appver=3.0.6; osver=10.0.19045` 与
   `os=android; appver=9.0.90; osver=14` 在三首样本上结果**完全一致**（含 hires 2.83 Mbps），
   没有“移动端身份被降级”的现象；`deviceId` / `channel` 在本矩阵内同样无影响。
3. **返回档位可能低于请求档位，且 `code` 仍为 200。**
   这通常是曲目自身的上限（例：某曲最高只有 320k），不一定是风控；客户端若静默接受，
   用户侧看不出“少拿了音质”，排查时容易误判。
4. **`freeTrialInfo` 非空即试听片段。**
   必须按取链失败处理（交给换源/提示），否则播放会在片段末尾中断。
5. `level` 与 `encodeType` 必须成对给出，否则服务端会按自身规则选择，
   例如请求 `lossless` 配 `mp3` 时静默回落。

## 实测矩阵

样本：`347230`（VIP 曲，最高 flac 797 kbps）、`1330348068`（最高 hires 2.83 Mbps）。
请求 `level=lossless&encodeType=flac`，匿名行与登录行使用同一 `deviceId`。

| Cookie | `347230` | `1330348068` |
|---|---|---|
| 无 | `code=200`，**`url=null`** | `exhigh/mp3/320000` |
| 仅 `deviceId` | `code=200`，**`url=null`** | `exhigh/mp3/320000` |
| `os=pc; appver=3.0.6` | `standard/mp3/128008` **TRIAL** | `exhigh/mp3/320000` |
| 上 + `deviceId` | 同上（无变化） | 同上 |
| 上 + `channel=netease` | 同上（无变化） | 同上 |
| 上 + `MUSIC_U` | **`lossless/flac/797831`** | **`hires/flac/2832352`** |
| `os=android; appver=9.0.90; osver=14; MUSIC_U` | 与 PC 身份一致 | 与 PC 身份一致 |

匿名上限：免费曲 320k（`exhigh`），VIP 曲 128k 试听或直接 `null`；要 flac/hires 必须登录。

## 复现

```bash
# 匿名：观察 url=null 与 128k 试听两种形态
curl -s -X POST 'https://interface3.music.163.com/api/song/enhance/player/url/v1' \
  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0' \
  -H 'Referer: https://music.163.com/' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'Cookie: os=pc; appver=3.0.6; osver=10.0.19045; deviceId=<32 hex>; channel=netease' \
  --data 'ids=[347230]&level=lossless&encodeType=flac'

# 登录：同一请求追加 MUSIC_U=<你的 cookie>，观察 lossless/hires
```

判读字段：`code`（外层）、`data[0].code`、`data[0].url`、`data[0].level`、`data[0].type`、
`data[0].br`、`data[0].freeTrialInfo`。

## 对实现的含义

- `HeaderInterceptor` 现有的 `os=android; appver=9.0.90` 已经够用；为“音质”改成 PC 身份没有收益。
  真正影响音质的是用户是否登录、账号是否 VIP。
- 取链失败的判定不能只看外层 `code`：`code=200 + url=null`、`code=200 + freeTrialInfo` 都属于失败。
- `AudioSourceRouter` 已用 `isFreeTrial` 作为换源条件，方向正确；建议同时把 `url.isBlank()`
  视作失败，覆盖“身份闸门未通过 → url 为 null”这一形态（此时官方通道其实什么都没给）。
