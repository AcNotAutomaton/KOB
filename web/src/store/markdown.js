import $ from "jquery"

export default {
    state: {
        markdown:''
    },
    getters: {
    },
    mutations: {
        updateMarkdown(state, data){
            state.markdown = data
        }
    },
    actions: {
         showMarkdown(context){
            $.ajax({
                url:"http://123.56.126.125:3000/api/user/account/showmarkdown/",
                type:"post",
                success(res){
                    context.commit("updateMarkdown",res)
                }
            })
        },

        updateMarkdown(context, value){
            $.ajax({
                url:"http://123.56.126.125:3000/api/user/account/updatemarkdown/",
                type:"post",
                headers: {
                    Authorization: "Bearer " + localStorage.getItem("jwt_token"),
                },
                data:{
                    markdown:value
                },
                success(res){
                    if(res===1)
                    context.commit("updateMarkdown",value)
                    

                }
            })
        }
    },
    modules: {
    }
}