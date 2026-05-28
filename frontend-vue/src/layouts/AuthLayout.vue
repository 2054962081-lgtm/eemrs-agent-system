<template>
  <main class="auth-layout">
    <section class="auth-intro">
      <div class="intro-panel">
        <div class="intro-badge">Encrypted Medical Records</div>
        <h1>密文电子医疗系统</h1>
        <p>面向患者与医生的统一 Web 入口，保留现有 JWT 权限隔离与 SM2 验签业务链路。</p>
      </div>
    </section>
    <section class="auth-content">
      <router-view />
    </section>
  </main>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(380px, 520px);
  background: linear-gradient(135deg, #eaf5ff 0%, #f5f7fb 54%, #eefaf8 100%);
}

.auth-intro,
.auth-content {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.intro-panel {
  max-width: 560px;
}

.intro-badge {
  display: inline-flex;
  padding: 7px 12px;
  border-radius: 999px;
  color: #15655e;
  background: #dff8f3;
  font-weight: 700;
  font-size: 13px;
}

h1 {
  margin: 24px 0 14px;
  font-size: 44px;
  color: #163457;
}

p {
  margin: 0;
  color: #52657d;
  font-size: 17px;
  line-height: 1.8;
}

@media (max-width: 860px) {
  .auth-layout {
    grid-template-columns: 1fr;
  }

  .auth-intro {
    padding-bottom: 0;
  }

  h1 {
    font-size: 32px;
  }
}
</style>
