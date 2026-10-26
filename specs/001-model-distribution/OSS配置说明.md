# 模型分发 · OSS 直链配置说明

> 面向**房主 / 服主**。客户端不需要任何配置。
> 相关代码：`common/src/main/java/com/shiroha/mmdskin/distrib/`（`ModelDistributionConfig`、`ModelDistributionService`、`ModelDistributionClient`）。

---

## 一、两种分发通道

模型分发有两条通道，**对玩家完全一样**（都是"模型选择器 → 服务器模型分发 → 勾选授权 → 下载"），
区别只在**字节从哪来**：

| | 服务器转发（默认） | OSS 直链 |
|---|---|---|
| 服务器做什么 | 把模型文件**逐块转发**给玩家 | **只发清单和校验值**，字节一个都不转发 |
| 服务器出口 | 与模型体积相同 | **0** |
| 玩家从哪拿文件 | 服务器 | 直接从 OSS/CDN |
| 配置 | 只需 `distributable: true` | 额外填 `ossUrl` |
| 适合 | 小局域网 / 自用 | 公网服 / 多人同时下载 |

OSS 直链的好处是**服务器出口为 0**：模型放在 OSS 上，玩家各自去拉，服务器只当"发号台"。

---

## 二、配置文件

位置：**服务端**（专用服务端 = 服务端工作目录；单人开房 = 房主的游戏目录）：

```
<服务端目录>/config/mmdskin/distribution.json
```

完整示例：

```json
{
  "acknowledged": true,
  "enabled": true,
  "rateLimitKbPerSec": 256,
  "globalRateLimitKbPerSec": 1024,
  "models": {
    "纳西妲": {
      "distributable": true,
      "ossUrl": "https://your-bucket.oss-cn-shanghai.aliyuncs.com/mmd/纳西妲"
    }
  }
}
```

| 字段 | 含义 |
|---|---|
| `acknowledged` | **责任确认位**：房主确认自己有权二次配布这些模型。不改这一行，分发功能完全不启动。 |
| `enabled` | 分发总开关。 |
| `rateLimitKbPerSec` | 单个客户端的限速（KB/s）。仅对**服务器转发**生效；OSS 直链不受服务器限速（限速在你自己家桶/CDN 上做）。 |
| `globalRateLimitKbPerSec` | 全部客户端合计限速（KB/s），同上。 |
| `models.<模型名>.distributable` | 该模型是否允许分发给联机玩家。 |
| `models.<模型名>.ossUrl` | 可选的 OSS 直链前缀。**留空 = 走服务器转发**。 |

改完**重启服务端**生效（客户端侧每 2 秒轮询配置，热更的是"清单内容"，`ossUrl` 改动需要重启服务端）。

---

## 三、配 OSS 直链的步骤

1. 把模型目录整个上传到 OSS。**目录结构要原样保留**，模型目录里的相对路径就是清单里的路径：

   ```
   <ossUrl>/纳西妲.pmx
   <ossUrl>/tex/体1.png
   <ossUrl>/tex/肌.png
   <ossUrl>/tex/颜.png
   <ossUrl>/tex/髮1.png
   <ossUrl>/tex/spa_h.png
   ...
   ```

   也就是说 `ossUrl` 是**前缀**，后面拼上模型目录内的相对路径就能取到文件。

2. 确认上传方式是**单次上传**（不是分片上传），让 OSS 的 **ETag 就是文件的 MD5**（见下一节，这是硬性要求）。

3. 在 `distribution.json` 里给该模型填上 `ossUrl` 和 `distributable: true`。

4. 重启服务端。服务端会对清单里的**第一个文件**发一个 `HEAD` 请求来校验：

   ```
   HEAD <ossUrl>/<清单第一个文件的相对路径>
   → 要求 ETag（去掉引号后）== 服务器本地该文件的 MD5
   ```

   校验通过 → 清单标记 `ossVerified = true`，之后**只发清单、服务器不再转发任何字节**；
   校验不通过 → **自动退回服务器转发**（功能不受影响，只是费服务器出口）。

---

## 四、硬性要求：ETag 必须是文件 MD5

阿里云 OSS 在**单次上传**（未分片）时，ETag 就是文件的 MD5 十六进制值 —— 这正是服务端用来
比对"OSS 上的文件"与"服务器本地副本"是否一致的依据。

- **分片上传的 ETag 形如 `xxxx-N`（带 `-数字` 后缀），不是 MD5** → 校验必然失败 → 退回服务器转发。
- 如果你的 OSS/CDN 返回的 ETag 不是 MD5（自建 Nginx、S3 兼容存储等），这条直链通道就用不了，
  留着 `ossUrl` 也不会出错，只是会自动退回转发。

排查时看服务端日志里的关键字：

```
OSS 直链探测失败（HTTP xxx），该模型退回服务器转发
OSS 直链没有 ETag，无法校验 MD5，该模型退回服务器转发
OSS 直链的 ETag 与服务器本地副本 MD5 不一致，该模型退回服务器转发
```

---

## 五、客户端怎么拿到文件

1. 玩家进服 → 服务端推**清单**（模型名、文件列表、每文件 MD5、总体积、限速、`ossUrl`）。
2. 玩家在模型选择器里看到「⬇ 服务器模型分发 (N)」→ 打开 → 勾选责任声明 → 下载。
3. 客户端按清单**直接去 `ossUrl` 拉每个文件**，逐个算 MD5 比对，校验通过后落盘到
   `<客户端>/3d-skin/EntityPlayer/<模型名>/`，最后改名成正式目录。
4. **兜底**：如果 OSS 拉取失败（404 / 超时 / 校验不过），客户端会**自动改用服务器转发补发**
   （日志：`改用服务器转发补发模型: <模型名>`），所以直链配错了最多是"没省到出口"，不会让玩家下不到。

> 落盘是"先写临时目录、全部校验通过再改名"的原子式安装，中途断线不会留下半个模型。

---

## 六、本机自检（不花钱、不上云）

仓库 `tools/` 下有两个脚本，可以在**本机**把整条 OSS 链路跑通：

| 脚本 | 作用 |
|---|---|
| `tools/oss_server.py` | 起一个模拟 OSS 的 HTTP 服务：按 OSS 单次上传的约定，用文件 **MD5 当 ETag**，并把百分号编码的路径解码（模型名是中文，必须解码）。默认服务 `D:\PCl\mmd-server-test\3d-skin\EntityPlayer\纳西妲` 的 8099 端口。 |
| `tools/check_oss.py` | 对若干文件发 `HEAD`，比对返回的 ETag 与本地 MD5 是否一致（应当全部 `一致=True`）。 |

跑法（**注意：脚本路径含中文，命令行传参会乱码，所以要用工作目录启动**）：

```powershell
$tools = 'D:\尝试文件夹\MC MMD\tools'
Start-Process python -ArgumentList 'oss_server.py' -WorkingDirectory $tools `
    -RedirectStandardOutput 'D:\PCl\mmd-server-test\oss-http.log' `
    -RedirectStandardError  'D:\PCl\mmd-server-test\oss-http-err.log' -WindowStyle Hidden
python "$tools\check_oss.py"
```

然后把服务端配置改成 `"ossUrl": "http://127.0.0.1:8099"`，重启服务端即可验证整条直链流程。

---

## 七、常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 客户端看不到「服务器模型分发」入口 | 服务端清单为空：`distributable` 没开，或服务端目录里根本没有该模型目录。 |
| 入口有，但下载页没有模型 | 服务端 `acknowledged`/`enabled` 没开，或模型名和 `models` 里的键对不上（**键是模型目录名**）。 |
| 走了 OSS 但服务器仍在发块 | ETag 校验没过（见第四节）。看服务端日志那三条 WARN。 |
| OSS 拉取失败 | 客户端会自动退回服务器转发；看客户端日志「改用服务器转发补发模型」。也检查 `ossUrl` 是否需要编码（中文模型名）。 |
| 玩家下载很慢 | OSS 直链不受 `rateLimitKbPerSec` 限制，慢就是 OSS 侧的事（桶的带宽/CDN）。用服务器转发时才会被限速。 |
