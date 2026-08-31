/**
 * 统一请求层：所有 HTTP 请求走同源相对路径（/api/**），
 * 开发环境由 vue.config.js 的 devServer 代理转发到后端，
 * 生产环境由 nginx 转发（system/nginx.conf 已配置 /api -> 127.0.0.1:3000）。
 *
 * 用法（与原 $.ajax 习惯对齐）：
 *   ajax({
 *     url: "/api/user/bot/getlist/",
 *     type: "get" | "post",
 *     data: {...},                  // 可选
 *     headers: {...},               // 可选，Authorization 会自动带上
 *     success(resp) {...},          // 可选
 *     error(resp) {...},            // 可选（HTTP 非 2xx 或网络失败）
 *   })
 */

function buildQuery(params) {
    const usp = new URLSearchParams();
    Object.entries(params || {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
            usp.append(key, value);
        }
    });
    const s = usp.toString();
    return s ? "?" + s : "";
}

export function ajax({ url, type = "get", data = {}, headers = {}, success, error }) {
    const token = localStorage.getItem("jwt_token");
    const authHeaders = token ? { Authorization: "Bearer " + token } : {};
    const method = type.toLowerCase();

    const options = {
        method,
        headers: { ...authHeaders, ...headers },
    };

    let requestUrl = url;
    if (method === "get") {
        requestUrl = url + buildQuery(data);
    } else {
        // 与后端 @RequestParam 表单接收方式保持一致
        options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
        options.body = new URLSearchParams(data).toString();
    }

    fetch(requestUrl, options)
        .then(async resp => {
            let body;
            // 后端 showmarkdown 等接口返回纯文本（text/plain），其余返回 JSON，按 content-type 区分
            const contentType = resp.headers.get("content-type") || "";
            try {
                if (contentType.indexOf("application/json") !== -1) {
                    body = await resp.json();
                } else {
                    body = await resp.text();
                }
            } catch (e) {
                body = {};
            }
            if (resp.ok) {
                if (success) success(body);
            } else {
                if (error) error(body);
            }
        })
        .catch(err => {
            console.error("请求失败：" + url, err);
            if (error) error({});
        });
}

/** WebSocket 地址：开发环境直连后端（devServer 代理 WS 需要额外配置），生产环境走同源 */
export function websocketUrl() {
    const proto = location.protocol === "https:" ? "wss://" : "ws://";
    const token = localStorage.getItem("jwt_token") || "";
    if (process.env.NODE_ENV === "development") {
        return `ws://127.0.0.1:3000/websocket/${token}/`;
    }
    return `${proto}${location.host}/websocket/${token}/`;
}
