<template>
    <ContentField>
        <VMdEditor v-if="is_show" v-model="markdown" @save="save"
            left-toolbar="undo redo clear | h bold italic strikethrough quote | ul ol table hr | link image code | save back"
            right-toolbar="preview toc sync-scroll fullscreen" :toolbar="toolbar" height="800px"></VMdEditor>
        <VMdPreview v-else :text="markdown"></VMdPreview>
    </ContentField>
    <button v-if="!is_show" @click="is_show = true" type="button" class="btn btn-primary too">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
            class="feather feather-edit-3">
            <path d="M12 20h9"></path>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
        </svg>
    </button>
</template>

<script setup>
import { defineAsyncComponent, reactive, ref, watch } from 'vue'
import ContentField from '../../components/ContentField.vue'
import { useStore } from 'vuex'
import VMdPreview from '@kangc/v-md-editor/lib/preview'
import '@kangc/v-md-editor/lib/style/preview.css'
import githubTheme from '@kangc/v-md-editor/lib/theme/github'
import '@kangc/v-md-editor/lib/theme/style/github.css'
import hljs from 'highlight.js'

VMdPreview.use(githubTheme, { Hljs: hljs })
const VMdEditor = defineAsyncComponent(async () => {
    const [editor] = await Promise.all([
        import('@kangc/v-md-editor'),
        import('@kangc/v-md-editor/lib/style/base-editor.css'),
    ])
    return editor.default.use(githubTheme, { Hljs: hljs })
})

const store = useStore()
const is_show = ref(false)
const markdown = ref("")

const toolbar = reactive({
    back: {
        title: '取消编辑',
        icon: 'v-md-icon-undo',
        action() {
            is_show.value = false
            markdown.value = store.state.markdown.markdown
        },
    },
})

watch(() => store.state.markdown.markdown, value => {
    if (!is_show.value) markdown.value = value
}, { immediate: true })

const save = () => {
    is_show.value = false
    store.dispatch("updateMarkdown", markdown.value)
}
</script>

<style scoped>
.too {
    position: absolute;
    bottom: 20px;
    right: 20px;
}
</style>
