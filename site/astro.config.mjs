import { defineConfig } from "astro/config";
import { satteri } from "@astrojs/markdown-satteri";

const BASE = "/Melodia";
const REPO_DOCS = "https://github.com/rinchao0721/Melodia/blob/main/docs/";
const DOC_SLUGS = new Set(["plugin-format", "skin-format", "plugin-catalog"]);

// docs/*.md 之间的相对链接改写成站内地址，其余相对路径指回 GitHub 仓库
const docLinks = () => ({
  name: "doc-links",
  element: {
    filter: ["a"],
    visit(node, ctx) {
      const href = node.properties?.href;
      if (typeof href !== "string" || /^([a-z][a-z0-9+.-]*:|#|\/)/i.test(href)) return;
      const m = href.match(/^(?:\.\/)?([\w-]+)\.md(#.*)?$/);
      ctx.setProperty(node, "href", m && DOC_SLUGS.has(m[1])
        ? `${BASE}/docs/${m[1]}/${m[2] ?? ""}`
        : new URL(href, REPO_DOCS).href);
    },
  },
});

export default defineConfig({
  site: "https://rinchao0721.github.io",
  base: BASE,
  markdown: {
    processor: satteri({ hastPlugins: [docLinks] }),
    shikiConfig: {
      themes: { light: "github-light", dark: "github-dark" },
      defaultColor: false,
    },
  },
});
