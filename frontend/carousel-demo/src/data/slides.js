// 占位图使用开源服务 Lorem Picsum（github.com/LucasBassetti/picsum-photos）
// 换成自己的素材时，把 image 改成本地路径或自己的图片 URL 即可
const picsum = (seed) => `https://picsum.photos/seed/${seed}/1280/720`

export const slides = [
  {
    tag: 'loop',
    title: '无限循环轮播',
    caption: '核心选项 loop: true，最后一帧自动接回第一帧',
    image: picsum('embla-loop')
  },
  {
    tag: 'autoplay',
    title: '自动播放',
    caption: 'embla-carousel-autoplay 插件，4 秒切换，鼠标悬停暂停',
    image: picsum('embla-autoplay')
  },
  {
    tag: 'wheel',
    title: '滚轮与手势',
    caption: '在画面上滚动鼠标滚轮切换，桌面拖拽、移动端滑动同样可用',
    image: picsum('embla-drag')
  },
  {
    tag: 'keyboard',
    title: '键盘导航',
    caption: '组件聚焦后按 ← / → 方向键切换，并带 focus-visible 轮廓',
    image: picsum('embla-keyboard')
  },
  {
    tag: 'progress',
    title: '进度与索引',
    caption: '用 emblaApi.scrollProgress() 驱动底部进度条，select 事件驱动圆点',
    image: picsum('embla-progress')
  }
]
