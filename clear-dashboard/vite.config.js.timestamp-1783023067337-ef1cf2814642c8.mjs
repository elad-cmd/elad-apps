// vite.config.js
import { defineConfig } from "file:///C:/Users/elad/Eshkol%20Bina%20Dropbox/Elad%20Schweitzer/claude_elad/apps/clear-dashboard/node_modules/vite/dist/node/index.js";
import react from "file:///C:/Users/elad/Eshkol%20Bina%20Dropbox/Elad%20Schweitzer/claude_elad/apps/clear-dashboard/node_modules/@vitejs/plugin-react/dist/index.js";
import { VitePWA } from "file:///C:/Users/elad/Eshkol%20Bina%20Dropbox/Elad%20Schweitzer/claude_elad/apps/clear-dashboard/node_modules/vite-plugin-pwa/dist/index.js";
var vite_config_default = defineConfig({
  // Relative base so the built app also works when served from a sub-path
  // (e.g. GitHub Pages project sites).
  base: "./",
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["icon-192.png", "icon-512.png", "apple-touch-icon.png"],
      manifest: {
        name: "\u05D3\u05E9\u05D1\u05D5\u05E8\u05D3 \u05DE\u05E9\u05D9\u05DE\u05D5\u05EA",
        short_name: "\u05DE\u05E9\u05D9\u05DE\u05D5\u05EA",
        description: "\u05D3\u05E9\u05D1\u05D5\u05E8\u05D3 \u05DE\u05E9\u05D9\u05DE\u05D5\u05EA \u05D1\u05E1\u05D2\u05E0\u05D5\u05DF Clear \u05E2\u05DD \u05DE\u05D7\u05D5\u05D5\u05EA \u05D4\u05D7\u05DC\u05E7\u05D4",
        lang: "he",
        dir: "rtl",
        theme_color: "#7BBFC0",
        background_color: "#7BBFC0",
        display: "standalone",
        orientation: "portrait",
        start_url: "./",
        scope: "./",
        icons: [
          { src: "icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" }
        ]
      },
      workbox: {
        globPatterns: ["**/*.{js,css,html,png,svg,woff2}"]
      }
    })
  ]
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcuanMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJDOlxcXFxVc2Vyc1xcXFxlbGFkXFxcXEVzaGtvbCBCaW5hIERyb3Bib3hcXFxcRWxhZCBTY2h3ZWl0emVyXFxcXGNsYXVkZV9lbGFkXFxcXGFwcHNcXFxcY2xlYXItZGFzaGJvYXJkXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ZpbGVuYW1lID0gXCJDOlxcXFxVc2Vyc1xcXFxlbGFkXFxcXEVzaGtvbCBCaW5hIERyb3Bib3hcXFxcRWxhZCBTY2h3ZWl0emVyXFxcXGNsYXVkZV9lbGFkXFxcXGFwcHNcXFxcY2xlYXItZGFzaGJvYXJkXFxcXHZpdGUuY29uZmlnLmpzXCI7Y29uc3QgX192aXRlX2luamVjdGVkX29yaWdpbmFsX2ltcG9ydF9tZXRhX3VybCA9IFwiZmlsZTovLy9DOi9Vc2Vycy9lbGFkL0VzaGtvbCUyMEJpbmElMjBEcm9wYm94L0VsYWQlMjBTY2h3ZWl0emVyL2NsYXVkZV9lbGFkL2FwcHMvY2xlYXItZGFzaGJvYXJkL3ZpdGUuY29uZmlnLmpzXCI7aW1wb3J0IHsgZGVmaW5lQ29uZmlnIH0gZnJvbSAndml0ZSc7XG5pbXBvcnQgcmVhY3QgZnJvbSAnQHZpdGVqcy9wbHVnaW4tcmVhY3QnO1xuaW1wb3J0IHsgVml0ZVBXQSB9IGZyb20gJ3ZpdGUtcGx1Z2luLXB3YSc7XG5cbmV4cG9ydCBkZWZhdWx0IGRlZmluZUNvbmZpZyh7XG4gIC8vIFJlbGF0aXZlIGJhc2Ugc28gdGhlIGJ1aWx0IGFwcCBhbHNvIHdvcmtzIHdoZW4gc2VydmVkIGZyb20gYSBzdWItcGF0aFxuICAvLyAoZS5nLiBHaXRIdWIgUGFnZXMgcHJvamVjdCBzaXRlcykuXG4gIGJhc2U6ICcuLycsXG4gIHBsdWdpbnM6IFtcbiAgICByZWFjdCgpLFxuICAgIFZpdGVQV0Eoe1xuICAgICAgcmVnaXN0ZXJUeXBlOiAnYXV0b1VwZGF0ZScsXG4gICAgICBpbmNsdWRlQXNzZXRzOiBbJ2ljb24tMTkyLnBuZycsICdpY29uLTUxMi5wbmcnLCAnYXBwbGUtdG91Y2gtaWNvbi5wbmcnXSxcbiAgICAgIG1hbmlmZXN0OiB7XG4gICAgICAgIG5hbWU6ICdcdTA1RDNcdTA1RTlcdTA1RDFcdTA1RDVcdTA1RThcdTA1RDMgXHUwNURFXHUwNUU5XHUwNUQ5XHUwNURFXHUwNUQ1XHUwNUVBJyxcbiAgICAgICAgc2hvcnRfbmFtZTogJ1x1MDVERVx1MDVFOVx1MDVEOVx1MDVERVx1MDVENVx1MDVFQScsXG4gICAgICAgIGRlc2NyaXB0aW9uOiAnXHUwNUQzXHUwNUU5XHUwNUQxXHUwNUQ1XHUwNUU4XHUwNUQzIFx1MDVERVx1MDVFOVx1MDVEOVx1MDVERVx1MDVENVx1MDVFQSBcdTA1RDFcdTA1RTFcdTA1RDJcdTA1RTBcdTA1RDVcdTA1REYgQ2xlYXIgXHUwNUUyXHUwNUREIFx1MDVERVx1MDVEN1x1MDVENVx1MDVENVx1MDVFQSBcdTA1RDRcdTA1RDdcdTA1RENcdTA1RTdcdTA1RDQnLFxuICAgICAgICBsYW5nOiAnaGUnLFxuICAgICAgICBkaXI6ICdydGwnLFxuICAgICAgICB0aGVtZV9jb2xvcjogJyM3QkJGQzAnLFxuICAgICAgICBiYWNrZ3JvdW5kX2NvbG9yOiAnIzdCQkZDMCcsXG4gICAgICAgIGRpc3BsYXk6ICdzdGFuZGFsb25lJyxcbiAgICAgICAgb3JpZW50YXRpb246ICdwb3J0cmFpdCcsXG4gICAgICAgIHN0YXJ0X3VybDogJy4vJyxcbiAgICAgICAgc2NvcGU6ICcuLycsXG4gICAgICAgIGljb25zOiBbXG4gICAgICAgICAgeyBzcmM6ICdpY29uLTE5Mi5wbmcnLCBzaXplczogJzE5MngxOTInLCB0eXBlOiAnaW1hZ2UvcG5nJyB9LFxuICAgICAgICAgIHsgc3JjOiAnaWNvbi01MTIucG5nJywgc2l6ZXM6ICc1MTJ4NTEyJywgdHlwZTogJ2ltYWdlL3BuZycgfSxcbiAgICAgICAgICB7IHNyYzogJ2ljb24tNTEyLnBuZycsIHNpemVzOiAnNTEyeDUxMicsIHR5cGU6ICdpbWFnZS9wbmcnLCBwdXJwb3NlOiAnbWFza2FibGUnIH1cbiAgICAgICAgXVxuICAgICAgfSxcbiAgICAgIHdvcmtib3g6IHtcbiAgICAgICAgZ2xvYlBhdHRlcm5zOiBbJyoqLyoue2pzLGNzcyxodG1sLHBuZyxzdmcsd29mZjJ9J11cbiAgICAgIH1cbiAgICB9KVxuICBdXG59KTtcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBOGIsU0FBUyxvQkFBb0I7QUFDM2QsT0FBTyxXQUFXO0FBQ2xCLFNBQVMsZUFBZTtBQUV4QixJQUFPLHNCQUFRLGFBQWE7QUFBQTtBQUFBO0FBQUEsRUFHMUIsTUFBTTtBQUFBLEVBQ04sU0FBUztBQUFBLElBQ1AsTUFBTTtBQUFBLElBQ04sUUFBUTtBQUFBLE1BQ04sY0FBYztBQUFBLE1BQ2QsZUFBZSxDQUFDLGdCQUFnQixnQkFBZ0Isc0JBQXNCO0FBQUEsTUFDdEUsVUFBVTtBQUFBLFFBQ1IsTUFBTTtBQUFBLFFBQ04sWUFBWTtBQUFBLFFBQ1osYUFBYTtBQUFBLFFBQ2IsTUFBTTtBQUFBLFFBQ04sS0FBSztBQUFBLFFBQ0wsYUFBYTtBQUFBLFFBQ2Isa0JBQWtCO0FBQUEsUUFDbEIsU0FBUztBQUFBLFFBQ1QsYUFBYTtBQUFBLFFBQ2IsV0FBVztBQUFBLFFBQ1gsT0FBTztBQUFBLFFBQ1AsT0FBTztBQUFBLFVBQ0wsRUFBRSxLQUFLLGdCQUFnQixPQUFPLFdBQVcsTUFBTSxZQUFZO0FBQUEsVUFDM0QsRUFBRSxLQUFLLGdCQUFnQixPQUFPLFdBQVcsTUFBTSxZQUFZO0FBQUEsVUFDM0QsRUFBRSxLQUFLLGdCQUFnQixPQUFPLFdBQVcsTUFBTSxhQUFhLFNBQVMsV0FBVztBQUFBLFFBQ2xGO0FBQUEsTUFDRjtBQUFBLE1BQ0EsU0FBUztBQUFBLFFBQ1AsY0FBYyxDQUFDLGtDQUFrQztBQUFBLE1BQ25EO0FBQUEsSUFDRixDQUFDO0FBQUEsRUFDSDtBQUNGLENBQUM7IiwKICAibmFtZXMiOiBbXQp9Cg==
