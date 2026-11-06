const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;

// 首屏三首示例曲目，封面用 Canvas 原创绘制
const songs = [
  { title: "夜航", artist: "示例曲目 · 原创封面", label: "01 / NIGHT FLIGHT",
    pal: [[27, 20, 66], [255, 128, 84], [236, 64, 128], [72, 44, 150]] },
  { title: "潮汐", artist: "示例曲目 · 原创封面", label: "02 / TIDES",
    pal: [[6, 44, 52], [44, 206, 172], [170, 238, 214], [22, 92, 128]] },
  { title: "回声", artist: "示例曲目 · 原创封面", label: "03 / ECHO",
    pal: [[60, 14, 28], [244, 176, 64], [212, 44, 64], [118, 40, 120]] },
];
// 设备区截图都在播 Morning Dew，光晕取它封面的几种绿
const shotPal = [
  [[52, 66, 24], [132, 152, 66], [190, 202, 170], [84, 104, 70]],
  [[60, 74, 26], [150, 166, 80], [206, 214, 186], [70, 92, 60]],
  [[40, 54, 20], [116, 138, 56], [176, 192, 150], [96, 116, 82]],
];
const COVER_FONT = 'ui-monospace, "SF Mono", "Cascadia Mono", Consolas, monospace';

const seeded = (seed) => () => {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const rgba = (c, a = 1) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;

function drawCover(kind, size = 720) {
  const cv = document.createElement("canvas");
  cv.width = cv.height = size;
  const g = cv.getContext("2d");
  if (!g) return "";
  const rnd = seeded(kind * 97 + 13);
  const S = size;
  if (kind === 0) {
    // 复古落日：渐变天空、被横条切开的太阳、星点
    const sky = g.createLinearGradient(0, 0, 0, S);
    sky.addColorStop(0, "#120d33"); sky.addColorStop(.62, "#3b1a63"); sky.addColorStop(1, "#5a1f5e");
    g.fillStyle = sky; g.fillRect(0, 0, S, S);
    for (let i = 0; i < 140; i++) {
      g.fillStyle = `rgba(255,255,255,${.2 + rnd() * .6})`;
      const r = rnd() * 1.6 + .3;
      g.beginPath(); g.arc(rnd() * S, rnd() * S * .55, r * S / 720, 0, Math.PI * 2); g.fill();
    }
    const cx = S * .5, cy = S * .58, r = S * .29;
    const sun = g.createLinearGradient(0, cy - r, 0, cy + r);
    sun.addColorStop(0, "#ffd27a"); sun.addColorStop(.5, "#ff8054"); sun.addColorStop(1, "#ec4080");
    g.save(); g.shadowColor = "rgba(255,110,90,.7)"; g.shadowBlur = S * .12;
    g.fillStyle = sun; g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.fill(); g.restore();
    for (let i = 0; i < 7; i++) {
      const y = cy + r * (.08 + i * .13);
      g.fillStyle = sky; g.fillRect(0, y, S, S * (.006 + i * .0045));
    }
    const ground = g.createLinearGradient(0, S * .8, 0, S);
    ground.addColorStop(0, "rgba(20,8,40,0)"); ground.addColorStop(1, "rgba(20,8,40,.95)");
    g.fillStyle = ground; g.fillRect(0, S * .78, S, S * .22);
    g.strokeStyle = "rgba(255,128,160,.45)"; g.lineWidth = S / 480;
    for (let i = 0; i < 9; i++) {
      const y = S * .86 + i * i * S * .0022;
      g.beginPath(); g.moveTo(0, y); g.lineTo(S, y); g.stroke();
    }
    for (let i = -8; i <= 8; i++) {
      g.beginPath(); g.moveTo(cx + i * S * .02, S * .86); g.lineTo(cx + i * S * .16, S); g.stroke();
    }
  } else if (kind === 1) {
    // 潮汐：层叠正弦线与一轮小月亮
    const bg = g.createRadialGradient(S * .5, S * .35, 0, S * .5, S * .5, S * .8);
    bg.addColorStop(0, "#0d4a50"); bg.addColorStop(1, "#03161b");
    g.fillStyle = bg; g.fillRect(0, 0, S, S);
    g.fillStyle = "rgba(220,255,240,.92)";
    g.save(); g.shadowColor = "rgba(170,238,214,.8)"; g.shadowBlur = S * .06;
    g.beginPath(); g.arc(S * .72, S * .24, S * .07, 0, Math.PI * 2); g.fill(); g.restore();
    const n = 34;
    for (let i = 0; i < n; i++) {
      const t = i / (n - 1);
      const y0 = S * (.42 + t * .54);
      const amp = S * (.008 + t * .045);
      const f = 2 + t * 1.5, ph = rnd() * Math.PI * 2;
      g.beginPath();
      for (let x = 0; x <= S; x += S / 180) {
        const u = x / S;
        const y = y0 + Math.sin(u * Math.PI * f + ph) * amp + Math.sin(u * Math.PI * 7 + ph * 2) * amp * .25;
        if (x === 0) g.moveTo(x, y); else g.lineTo(x, y);
      }
      const c = [Math.round(44 + 126 * (1 - t)), Math.round(206 + 32 * (1 - t)), Math.round(172 + 42 * (1 - t))];
      g.strokeStyle = rgba(c, .25 + .65 * t);
      g.lineWidth = (S / 720) * (1 + t * 2.2);
      g.stroke();
    }
  } else {
    // 回声：暖色网格渐变与偏心同心圆
    g.fillStyle = "#2a0812"; g.fillRect(0, 0, S, S);
    g.globalCompositeOperation = "screen";
    const blobs = [[.25, .3, "#f4b040"], [.78, .25, "#d42c40"], [.6, .78, "#7a2880"], [.18, .82, "#ff6a3a"], [.5, .5, "#f07a50"]];
    blobs.forEach(([x, y, c]) => {
      const gr = g.createRadialGradient(x * S, y * S, 0, x * S, y * S, S * .55);
      gr.addColorStop(0, c); gr.addColorStop(1, "rgba(0,0,0,0)");
      g.fillStyle = gr; g.fillRect(0, 0, S, S);
    });
    g.globalCompositeOperation = "source-over";
    const ox = S * .36, oy = S * .6;
    for (let i = 1; i <= 16; i++) {
      g.strokeStyle = `rgba(255,240,220,${.42 - i * .022})`;
      g.lineWidth = (S / 720) * (i < 3 ? 2.4 : 1.2);
      g.beginPath(); g.arc(ox, oy, S * .04 * i * (1 + i * .04), 0, Math.PI * 2); g.stroke();
    }
    g.fillStyle = "rgba(255,244,230,.95)";
    g.beginPath(); g.arc(ox, oy, S * .028, 0, Math.PI * 2); g.fill();
  }
  // 颗粒
  const img = g.getImageData(0, 0, S, S);
  const d = img.data;
  for (let i = 0; i < d.length; i += 4) {
    const v = (rnd() - .5) * 18;
    d[i] += v; d[i + 1] += v; d[i + 2] += v;
  }
  g.putImageData(img, 0, 0);
  g.fillStyle = "rgba(255,255,255,.82)";
  g.font = `500 ${Math.round(S * .026)}px ${COVER_FONT}`;
  g.textBaseline = "alphabetic";
  g.fillText("MELODIA", S * .06, S * .08);
  g.textAlign = "right";
  g.fillText(songs[kind].label, S * .94, S * .94);
  return cv.toDataURL("image/jpeg", .9);
}

function paintCovers() {
  const urls = songs.map((_, i) => drawCover(i));
  $$(".cover-art").forEach((a, i) => {
    if (urls[i]) a.style.backgroundImage = `url(${urls[i]})`;
  });
  const mini = $("#mini-art");
  if (mini && urls[0]) mini.style.backgroundImage = `url(${urls[0]})`;
}
paintCovers();

let isLight = document.documentElement.dataset.mode === "light";
addEventListener("melodia:themechange", (e) => { isLight = e.detail === "light"; });

/* ---------- 光晕 ---------- */
const aurora = (() => {
  const canvas = $(".aurora");
  const ctx = canvas?.getContext("2d");
  const W = 120, H = 80;
  if (canvas) { canvas.width = W; canvas.height = H; }
  let cur = songs[0].pal.map((c) => c.slice());
  let target = songs[0].pal;
  let px = .7, py = .3, tpx = .7, tpy = .3;
  addEventListener("pointermove", (e) => { tpx = e.clientX / innerWidth; tpy = e.clientY / innerHeight; }, { passive: true });
  const mixW = (c, m) => c.map((v) => Math.round(lerp(v, 255, m)));
  return {
    setTarget(p) { target = p; },
    setOpacity(o) { if (canvas) canvas.style.opacity = String(o); },
    draw(now, dt) {
      if (!ctx) return;
      const k = 1 - Math.exp(-dt * 2.2);
      cur = cur.map((c, i) => c.map((v, j) => lerp(v, target[i][j], k)));
      px += (tpx - px) * (1 - Math.exp(-dt * 3));
      py += (tpy - py) * (1 - Math.exp(-dt * 3));
      const s = reduce ? 0 : now / 1000;
      ctx.globalCompositeOperation = "source-over";
      const base = isLight ? mixW(cur[0], .8) : cur[0].map((v) => Math.round(v * .22));
      ctx.fillStyle = `rgb(${base})`;
      ctx.fillRect(0, 0, W, H);
      ctx.globalCompositeOperation = isLight ? "source-over" : "screen";
      const blobs = cur.map((c, i) => ({
        c,
        x: .5 + .38 * Math.sin(s * (.12 + i * .05) + i * 1.7),
        y: .5 + .4 * Math.cos(s * (.1 + i * .04) + i * 2.3),
        r: .5 + .12 * Math.sin(s * .2 + i),
      }));
      blobs.push({ c: cur[1], x: px, y: py, r: .32 });
      blobs.forEach((b) => {
        const col = isLight ? mixW(b.c, .42) : b.c;
        const a = isLight ? .55 : .8;
        const gr = ctx.createRadialGradient(b.x * W, b.y * H, 0, b.x * W, b.y * H, b.r * W);
        gr.addColorStop(0, `rgba(${col},${a})`);
        gr.addColorStop(1, `rgba(${col},0)`);
        ctx.fillStyle = gr;
        ctx.fillRect(0, 0, W, H);
      });
    },
  };
})();

/* ---------- 首屏播放器 ---------- */
const hero = (() => {
  const lines = $$(".line");
  const track = $(".lyrics-track");
  const box = $("#lyrics");
  const arts = $$(".cover-art");
  const cover = $("#cover");
  const seek = $("#seek");
  const playBtn = $("#play");
  const playIcon = $("#play-icon");
  const now = $(".now");
  const tNow = $("#t-now");
  const D = 3.4, T = D * lines.length;
  let pos = 0, playing = !reduce, idx = -1, song = 0;
  const fmt = (t) => `${Math.floor(t / 60)}:${String(Math.floor(t % 60)).padStart(2, "0")}`;
  $("#t-all").textContent = fmt(T);
  const ICON_PAUSE = '<path d="M7 5h4v14H7zM13 5h4v14h-4z"/>';
  const ICON_PLAY = '<path d="M8 5v14l11-7z"/>';
  const setPlaying = (v) => {
    playing = v;
    playIcon.innerHTML = v ? ICON_PAUSE : ICON_PLAY;
    playBtn.setAttribute("aria-label", v ? "暂停" : "播放");
    now.classList.toggle("paused", !v);
  };
  const place = () => {
    if (idx < 0) return;
    const lh = lines[0].offsetHeight;
    track.style.transform = `translateY(${box.clientHeight / 2 - lh / 2 - idx * lh}px)`;
  };
  const render = () => {
    const i = Math.min(lines.length - 1, Math.floor(pos / D));
    const k = (pos - i * D) / D;
    if (i !== idx) {
      idx = i;
      lines.forEach((l, n) => l.classList.toggle("on", n === i));
      place();
      const s = Number(lines[i].dataset.song) || 0;
      if (s !== song) {
        song = s;
        arts.forEach((a, n) => a.classList.toggle("on", n === s));
        $("#song-title").textContent = songs[s].title;
        $("#song-artist").textContent = songs[s].artist;
        $("#now-text").textContent = `正在播放 · ${songs[s].title}`;
      }
    }
    lines[i].style.setProperty("--k", k.toFixed(3));
    seek.value = String(Math.round((pos / T) * 1000));
    seek.style.setProperty("--fill", `${(pos / T) * 100}%`);
    tNow.textContent = fmt(pos);
  };
  lines.forEach((l, i) => l.addEventListener("click", () => { pos = i * D; render(); }));
  $("#prev").addEventListener("click", () => {
    const i = Math.floor(pos / D);
    pos = (pos - i * D > D * .25 ? i : (i - 1 + lines.length) % lines.length) * D;
    render();
  });
  $("#next").addEventListener("click", () => { pos = ((Math.floor(pos / D) + 1) % lines.length) * D; render(); });
  playBtn.addEventListener("click", () => setPlaying(!playing));
  seek.addEventListener("input", () => { pos = (Number(seek.value) / 1000) * T * .9999; render(); });
  // 封面跟随指针轻微倾斜
  const wrap = cover.parentElement;
  wrap.addEventListener("pointermove", (e) => {
    if (reduce) return;
    const r = cover.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
    cover.style.transform = `rotateY(${x * 14}deg) rotateX(${-y * 14}deg)`;
  });
  wrap.addEventListener("pointerleave", () => { cover.style.transform = ""; });
  setPlaying(playing);
  render();
  return {
    tick(dt) { if (playing) { pos = (pos + dt) % T; render(); } },
    place,
    palette: () => songs[song].pal,
  };
})();

/* ---------- 设备变形 ---------- */
const morph = (() => {
  const sec = $("#devices");
  const stage = $("#stage");
  const frame = $("#frame");
  const screen = $("#screen");
  const shots = $$(".shot", screen);
  const caps = $$(".cap");
  const tag = $("#frame-tag");
  // 原始截图像素尺寸，不带设备边框
  const sizesPx = [[1344, 2992], [2560, 1600], [1920, 1040]];
  const aspects = sizesPx.map(([w, h]) => w / h);
  const tags = sizesPx.map(([w, h]) => `${w} × ${h}`);
  const spans = [[0, 1 / 3], [1 / 3, 2 / 3], [2 / 3, 1]];
  const sizes = () => {
    const sw = stage.clientWidth, sh = stage.clientHeight;
    const narrow = innerWidth <= 900;
    const ph = Math.min(sh * .92, 700), pw = ph * aspects[0];
    const tw = Math.min(sw * .96, sh * .86 * aspects[1]);
    const dw = Math.min(sw * (narrow ? 1 : 1.05), sh * .9 * aspects[2]);
    const k = Math.min(1, ph / 600);
    return [
      { w: pw, h: ph, r: 46 * k, b: 9 * k },
      { w: tw, h: tw / aspects[1], r: 26, b: Math.max(8, tw * .018) },
      { w: dw, h: dw / aspects[2], r: 12, b: 3 },
    ];
  };
  // 滚动只决定停在哪台设备，切换动画按时间播完，滚轮停在中途也不会卡在两张图叠着的半截
  const easeInOut = (t) => (t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
  let s = 0, from = 0, to = 0, startAt = 0, duration = 0;
  const retarget = (target, now) => {
    if (target === to) return;
    from = s;
    to = target;
    startAt = now;
    duration = reduce ? 0 : 650 + 250 * (Math.abs(to - from) - 1);
  };
  const render = () => {
    const z = sizes();
    const i = Math.min(1, Math.floor(s)), t = s - i;
    const A = z[i], B = z[i + 1];
    const w = lerp(A.w, B.w, t), h = lerp(A.h, B.h, t), rad = lerp(A.r, B.r, t), bz = lerp(A.b, B.b, t);
    Object.assign(frame.style, { width: `${w + bz * 2}px`, height: `${h + bz * 2}px`, borderRadius: `${rad}px`, padding: `${bz}px` });
    screen.style.borderRadius = `${Math.max(2, rad - bz)}px`;
    shots.forEach((sh, n) => {
      const a = aspects[n];
      const cw = w / h > a ? w : h * a;
      sh.style.width = `${cw}px`;
      sh.style.height = `${cw / a}px`;
      sh.style.opacity = String(clamp(1 - (Math.abs(s - n) - .28) / .4));
    });
    tag.textContent = tags[Math.round(s)];
  };
  const layout = (now = performance.now()) => {
    const r = sec.getBoundingClientRect();
    const p = clamp(-r.top / (r.height - innerHeight));
    const cur = p < 1 / 3 ? 0 : p < 2 / 3 ? 1 : 2;
    retarget(cur, now);
    caps.forEach((c, n) => {
      c.classList.toggle("on", n === cur);
      if (n === cur) c.style.setProperty("--k", clamp(((p - spans[n][0]) / (spans[n][1] - spans[n][0])) * 1.4).toFixed(3));
    });
    // 唱完首屏后由设备区接管光晕颜色
    aurora.setTarget(r.top < innerHeight * .5 ? shotPal[cur] : hero.palette());
    const end = sec.offsetTop + sec.offsetHeight - innerHeight;
    aurora.setOpacity(1 - .78 * clamp((scrollY - end) / innerHeight));
    tick(now);
  };
  const tick = (now, force = false) => {
    const t = duration > 0 ? clamp((now - startAt) / duration) : 1;
    const next = lerp(from, to, easeInOut(t));
    if (!force && next === s && t >= 1 && frame.style.width) return;
    s = next;
    render();
  };
  return { layout, tick };
})();

/* ---------- 特性高亮 ---------- */
const feats = $$(".feat");
const highlight = () => {
  const mid = innerHeight * .5;
  let best = null, bd = Infinity;
  feats.forEach((f) => {
    const r = f.getBoundingClientRect();
    const d = Math.abs(r.top + r.height / 2 - mid);
    if (d < bd) { bd = d; best = f; }
  });
  feats.forEach((f) => f.classList.toggle("on", f === best && bd < innerHeight * .3));
};

/* ---------- 截图横滑 ---------- */
const rail = $("#rail");
const step = () => rail.clientWidth * .7;
$("#rail-prev").addEventListener("click", () => rail.scrollBy({ left: -step(), behavior: reduce ? "auto" : "smooth" }));
$("#rail-next").addEventListener("click", () => rail.scrollBy({ left: step(), behavior: reduce ? "auto" : "smooth" }));
let drag = null;
rail.addEventListener("pointerdown", (e) => {
  if (e.pointerType !== "mouse") return;
  drag = { x: e.clientX, left: rail.scrollLeft };
  rail.setPointerCapture(e.pointerId);
});
rail.addEventListener("pointermove", (e) => {
  if (!drag) return;
  const dx = e.clientX - drag.x;
  if (Math.abs(dx) > 4) rail.classList.add("dragging");
  rail.scrollLeft = drag.left - dx;
});
const endDrag = () => { drag = null; rail.classList.remove("dragging"); };
rail.addEventListener("pointerup", endDrag);
rail.addEventListener("pointercancel", endDrag);

/* ---------- 深浅对比 ---------- */
const cmpTabs = $$("[data-cmp]");
const cmpPanels = $$("[data-cmp-panel]");
cmpTabs.forEach((t) => t.addEventListener("click", () => {
  cmpTabs.forEach((x) => x.setAttribute("aria-selected", String(x === t)));
  cmpPanels.forEach((p) => { p.hidden = p.dataset.cmpPanel !== t.dataset.cmp; });
}));
cmpPanels.forEach((p) => {
  const input = p.querySelector("input");
  input?.addEventListener("input", () => p.style.setProperty("--x", `${input.value}%`));
});

/* ---------- 皮肤试穿 ---------- */
const skins = $$(".skin");
const mini = $("#mini");
const json = $("#skin-json");
const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const applySkin = (btn) => {
  skins.forEach((b) => b.setAttribute("aria-pressed", String(b === btn)));
  const style = document.documentElement.style;
  const accent = btn.dataset.accent;
  if (accent) {
    style.setProperty("--accent", accent);
    style.setProperty("--on-accent", btn.dataset.on || "#FFFFFF");
  } else {
    style.removeProperty("--accent");
    style.removeProperty("--on-accent");
  }
  mini.style.setProperty("--skin-pane", btn.dataset.pane);
  const shown = accent || "#C20C0C";
  json.innerHTML = `<span class="n">// skin.json</span>
{
  <span class="k">"id"</span>: <span class="s">"${esc(btn.dataset.id)}"</span>,
  <span class="k">"name"</span>: <span class="s">"${esc(btn.dataset.name)}"</span>,
  <span class="k">"colors"</span>: {
    <span class="k">"dark"</span>: { <span class="k">"pane"</span>: <span class="s">"${esc(btn.dataset.pane)}"</span>, <span class="k">"accent"</span>: <span class="s">"${esc(shown)}"</span> }
  }
}`;
};
skins.forEach((b) => b.addEventListener("click", () => applySkin(b)));
if (skins.length) applySkin(skins[0]);

/* ---------- 驱动 ---------- */
const onScroll = () => {
  morph.layout();
  highlight();
};
let last = performance.now();
const frameLoop = (t) => {
  const dt = Math.min(.1, (t - last) / 1000);
  last = t;
  hero.tick(dt);
  morph.tick(t);
  aurora.draw(t, dt);
  requestAnimationFrame(frameLoop);
};
const redrawStatic = () => aurora.draw(performance.now(), 1);

addEventListener("scroll", () => { onScroll(); if (reduce) redrawStatic(); }, { passive: true });
addEventListener("resize", () => { hero.place(); morph.layout(); morph.tick(performance.now(), true); });
addEventListener("melodia:themechange", () => { if (reduce) redrawStatic(); });
if (document.fonts?.ready) document.fonts.ready.then(() => { hero.place(); onScroll(); });
onScroll();
if (reduce) {
  redrawStatic();
  ["click", "input"].forEach((ev) => document.addEventListener(ev, () => { onScroll(); redrawStatic(); }));
} else {
  requestAnimationFrame(frameLoop);
}
