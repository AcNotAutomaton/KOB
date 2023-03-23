<template>
    <ContentField>
        <v-md-editor v-if="is_show" v-model="markdown" @save = "save"  
        left-toolbar="undo redo clear | h bold italic strikethrough quote | ul ol table hr | link image code | save back" 
        right-toolbar="preview toc sync-scroll fullscreen"
        :toolbar="toolbar"
        height="800px"></v-md-editor>
        <v-md-preview v-else :text="markdown"></v-md-preview >  
    </ContentField>
    <button v-if="!is_show"  @click="is_show=true" type="button" class="btn btn-primary too">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-edit-3"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>
    </button>
</template>

<script>
import { onMounted, reactive, toRefs } from 'vue'
import ContentField from '../../components/ContentField.vue'
import { useStore } from 'vuex'


export default {
    components: {
        ContentField
    },
    setup(){
        const store = useStore()
        const vue = reactive({
            is_show:false,
            markdown:"",
            toolbar: {
          back: {
              title: '取消编辑',
              icon: 'v-md-icon-undo',
              action() {
                vue.is_show = false
                vue.markdown = store.state.markdown.markdown
              },
          },
          },
        })
        onMounted(()=>{
            vue.markdown = store.state.markdown.markdown
        })
        const save = ()=>{
            vue.is_show = false
            store.dispatch("updateMarkdown",vue.markdown)
        }
        return {
            ...toRefs(vue),
            save

        }
    }

}
</script>

<style scoped>
    .too{
        position: absolute;
        bottom: 20px;
        right: 20px;
    }
</style>
