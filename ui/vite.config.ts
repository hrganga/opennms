import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import svgLoader from 'vite-svg-loader'

export default defineConfig({
  resolve: {
    alias: {
      '@/': new URL('./src/', import.meta.url).pathname,
      '~@featherds': '@featherds'
    }
  },
  plugins: [vue({
    template: {
      compilerOptions: {
        isCustomElement: tag => tag.includes('rapi-doc')
      }
    }
  }), svgLoader()],
  define: {
    'process.env': process.env
  }
})
