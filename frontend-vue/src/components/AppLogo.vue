<template>
  <div class="app-logo">
    <div class="logo-mark">医</div>
    <div>
      <strong>密文电子医疗系统</strong>
      <span>Encrypted EMRS</span>
    </div>
  </div>
</template>

<style scoped>
.app-logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(135deg, #1d74d6, #1bb8a9);
}

strong,
span {
  display: block;
}

strong {
  font-size: 16px;
}

span {
  margin-top: 2px;
  font-size: 12px;
  color: #8ba0b8;
}
</style>
