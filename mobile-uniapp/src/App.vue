<script>
export default {
  onLaunch() {
    console.log('App Launch')
  },
  onShow() {
    console.log('App Show')
  },
  onHide() {
    console.log('App Hide')
  }
}
</script>

<style>
@import './styles/tokens.css';
@import './styles/content.css';

/* 全局基础 — 艺术培训风格 */
page {
  background-color: #FAF8F5;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC',
    'Noto Sans SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
  font-size: 28rpx;
  color: #4D4139;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

/* 安全区底部 */
.safe-area-bottom {
  padding-bottom: env(safe-area-inset-bottom);
}
</style>
