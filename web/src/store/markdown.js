import { ajax } from '../utils/requests'

export default {
    state: {
        markdown: ''
    },
    getters: {
    },
    mutations: {
        updateMarkdown(state, data) {
            state.markdown = data
        }
    },
    actions: {
        showMarkdown(context) {
            ajax({
                url: "/api/user/account/showmarkdown/",
                type: "post",
                success(res) {
                    context.commit("updateMarkdown", res)
                }
            })
        },

        updateMarkdown(context, value) {
            ajax({
                url: "/api/user/account/updatemarkdown/",
                type: "post",
                data: {
                    markdown: value
                },
                success(res) {
                    if (Number(res) === 1)
                        context.commit("updateMarkdown", value)
                }
            })
        }
    },
    modules: {
    }
}
