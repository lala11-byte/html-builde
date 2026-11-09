import Carousel from './components/Carousel.jsx'
import { slides } from './data/slides.js'

export default function App() {
  return (
    <main className="page">
      <header className="page__header">
        <p className="page__eyebrow">React + Vite + Embla Carousel</p>
        <h1 className="page__title">前端轮换展示 Demo</h1>
        <p className="page__desc">
          支持循环播放、自动轮播（悬停暂停）、滚轮滚动切换、拖拽滑动、圆点跳转、键盘 ← → 与进度条。
        </p>
      </header>

      <Carousel slides={slides} interval={4000} />

      <footer className="page__footer">
        <span>轮播库：<a href="https://github.com/davidjerleke/embla-carousel" target="_blank" rel="noreferrer">embla-carousel</a></span>
        <span>占位图来自开源服务 Lorem Picsum，替换 <code>src/data/slides.js</code> 即可换成自己的素材</span>
      </footer>
    </main>
  )
}
