import { ajax } from '../utils/requests'

let restoringLogin = null;

export default {
    state: {
        id: "",
        username: "",
        photo: "",
        token: "",
        is_login: false,
        pulling_info: true,  // 是否正在从云端拉取信息
    },
    getters: {
    },
    mutations: {
        updateUser(state, user) {
            state.id = user.id;
            state.username = user.username;
            state.photo = user.photo;
            state.is_login = user.is_login;
        },
        updateToken(state, token) {
            state.token = token;
        },
        logout(state) {
            state.id = "";
            state.username = "";
            state.photo = "";
            state.token = "";
            state.is_login = false;
        },
        updatePullingInfo(state, pulling_info) {
            state.pulling_info = pulling_info;
        }
    },
    actions: {
        login(context, data) {
            ajax({
                url: "/api/user/account/token/",
                type: "post",
                data: {
                    username: data.username,
                    password: data.password,
                },
                success(resp) {
                    if (resp.error_message === "success") {
                        localStorage.setItem("jwt_token", resp.token);
                        context.commit("updateToken", resp.token);
                        data.success(resp);
                    } else {
                        data.error(resp);
                    }
                },
                error(resp) {
                    data.error(resp);
                }
            });
        },
        getinfo(context, data) {
            const token = context.state.token;
            ajax({
                url: "/api/user/account/info/",
                type: "get",
                success(resp) {
                    // 退出或切换账号后，旧请求不得重新恢复原来的登录状态。
                    if (!token || token !== context.state.token || token !== localStorage.getItem("jwt_token")) {
                        data.error({ error_message: "登录状态已变化" });
                        return;
                    }
                    if (resp.error_message === "success") {
                        context.commit("updateUser", {
                            ...resp,
                            is_login: true,
                        });
                        data.success(resp);
                    } else {
                        data.error(resp);
                    }
                },
                error(resp) {
                    data.error(resp);
                }
            })
        },
        restoreLogin(context) {
            if (!context.state.pulling_info || context.state.is_login) return context.state.is_login;
            if (restoringLogin) return restoringLogin;
            const token = localStorage.getItem("jwt_token");
            if (!token) {
                context.commit("updatePullingInfo", false);
                return false;
            }
            context.commit("updateToken", token);
            restoringLogin = new Promise(resolve => {
                context.dispatch("getinfo", {
                    success() { resolve(true); },
                    error() {
                        if (context.state.token === token) context.dispatch("logout");
                        resolve(false);
                    },
                });
            }).finally(() => {
                context.commit("updatePullingInfo", false);
                restoringLogin = null;
            });
            return restoringLogin;
        },
        logout(context) {
            localStorage.removeItem("jwt_token");
            context.commit("logout");
            context.commit("updatePullingInfo", false);
            const socket = context.rootState.pk.socket;
            context.commit("updateSocket", null);
            context.commit("updateMatching", false);
            context.commit("updateStatus", "matching");
            context.commit("updateLoser", "none");
            if (socket) {
                socket.onopen = null;
                socket.onmessage = null;
                socket.onclose = null;
                socket.close();
            }
        }
    },
    modules: {
    }
}
