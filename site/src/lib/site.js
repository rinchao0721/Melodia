export const REPO = "https://github.com/rinchao0721/Melodia";
export const RELEASES = `${REPO}/releases`;

export const withBase = (path = "") => {
  const base = import.meta.env.BASE_URL.replace(/\/$/, "");
  return `${base}/${path.replace(/^\//, "")}`;
};

// 文档在侧栏和首页卡片里的顺序
export const DOCS = [
  { slug: "plugin-format", title: "插件格式", code: "plugin.json", desc: "包结构、清单字段、权限、沙盒脚本与原生插件。" },
  { slug: "skin-format", title: "皮肤格式", code: "skin.json", desc: "配色、背景图、圆角和字体，Android 与桌面端通用。" },
  { slug: "plugin-catalog", title: "插件目录", code: "index.json", desc: "应用内插件目录的格式，以及怎样收录你的插件。" },
];
