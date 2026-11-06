import { defineCollection } from "astro:content";
import { glob } from "astro/loaders";

// 直接读取仓库 docs/ 下的文档，不复制
const docs = defineCollection({
  loader: glob({ pattern: ["plugin-format.md", "skin-format.md", "plugin-catalog.md"], base: "../docs" }),
});

export const collections = { docs };
