const { defineConfig } = require('@vue/cli-service')

// 开发环境下，前端跑在 8080，API 由代理转发到后端 3000，
// 前端代码统一使用相对路径 /api/**，生产环境由 nginx 承担同样的转发职责
module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:3000',
        changeOrigin: true
      }
    }
  }
})
