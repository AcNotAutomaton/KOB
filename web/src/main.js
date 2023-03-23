import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'

//Preview  渲染
import VMdPreview from '@kangc/v-md-editor/lib/preview';
import '@kangc/v-md-editor/lib/style/preview.css';

//editor  编辑
import VueMarkdownEditor from '@kangc/v-md-editor';
import '@kangc/v-md-editor/lib/style/base-editor.css';

// 引入你所使用的主题 此处以 github 主题为例
import githubTheme from '@kangc/v-md-editor/lib/theme/github';
import '@kangc/v-md-editor/lib/theme/style/github.css';

// highlightjs
import hljs from 'highlight.js';
VueMarkdownEditor.use(githubTheme, {
    Hljs: hljs,
});
VMdPreview.use(githubTheme, {
    Hljs: hljs,
});
const app = createApp(App)

app.use(VMdPreview)
app.use(VueMarkdownEditor)
app.use(store)
app.use(router)
app.mount('#app')
