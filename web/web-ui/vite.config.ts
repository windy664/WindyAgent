import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteSingleFile } from 'vite-plugin-singlefile'

// 产物：dist/index.html —— 一个自包含的单文件（Vue 运行时 + 组件 + CSS 全内联）。
// gradle 的 syncWebUi 任务会把它复制并改名成 web/src/main/resources/dashboard-next.html。
//
// dev：`npm run dev` 起 Vite 开发服，/api 反向代理到本机运行中的 Velocity（web.port，默认 25580）。
//      改 target 成你的实际 web.port。
export default defineConfig({
  plugins: [vue(), viteSingleFile()],
  build: {
    target: 'es2018',
    cssCodeSplit: false,
    assetsInlineLimit: 100_000_000,
    chunkSizeWarningLimit: 100_000,
    rollupOptions: { output: { inlineDynamicImports: true } },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:25580', changeOrigin: true },
    },
  },
})
