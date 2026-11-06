const STORAGE_KEY = "melodia-theme";
const CHOICES = ["system", "light", "dark"];

const root = document.documentElement;
const mq = matchMedia("(prefers-color-scheme: dark)");
const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
const buttons = [...document.querySelectorAll("[data-theme-choice]")];

const resolve = (choice) => (choice === "light" || choice === "dark" ? choice : mq.matches ? "dark" : "light");
const effective = () => resolve(root.getAttribute("data-theme"));

const syncMode = () => {
  root.dataset.mode = effective();
  window.dispatchEvent(new CustomEvent("melodia:themechange", { detail: root.dataset.mode }));
};

const apply = (choice, persist) => {
  if (choice === "system") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", choice);
  buttons.forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.themeChoice === choice)));
  syncMode();
  if (!persist) return;
  try {
    localStorage.setItem(STORAGE_KEY, choice);
  } catch (e) {
    // 存储不可用时只在本次浏览生效
  }
};

buttons.forEach((b) => b.addEventListener("click", () => {
  const choice = b.dataset.themeChoice;
  if (!CHOICES.includes(choice)) return;
  if (resolve(choice) === effective() || reduce || typeof document.startViewTransition !== "function") {
    apply(choice, true);
    return;
  }
  // 与应用一致：新主题从按钮处圆形扩散，300ms
  const r = b.getBoundingClientRect();
  const x = r.left + r.width / 2;
  const y = r.top + r.height / 2;
  const transition = document.startViewTransition(() => apply(choice, true));
  transition.ready.then(() => {
    const radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
    root.animate(
      { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
      { duration: 300, easing: "ease-in-out", pseudoElement: "::view-transition-new(root)" },
    );
  }).catch(() => {});
}));

mq.addEventListener("change", syncMode);

let saved = "system";
try {
  saved = localStorage.getItem(STORAGE_KEY) || "system";
} catch (e) {
  saved = "system";
}
apply(CHOICES.includes(saved) ? saved : "system", false);

// 首页顶栏滚动后才变成毛玻璃，文档页一直是
const topbar = document.querySelector(".topbar");
if (topbar && !topbar.hasAttribute("data-sticky-solid")) {
  const onScroll = () => topbar.classList.toggle("solid", scrollY > 12);
  addEventListener("scroll", onScroll, { passive: true });
  onScroll();
}
