<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api'
import type { Address, Preference, Product, Profile } from '@/types'
import { useAuthStore } from '@/stores/auth'
import ProductCard from '@/components/ProductCard.vue'
import { resolveAssetUrl } from '@/api/http'

const router = useRouter()
const auth = useAuthStore()
const active = ref('overview')
const loading = ref(true)
const profile = ref<Profile | null>(null)
const addresses = ref<Address[]>([])
const favorites = ref<Product[]>([])
const history = ref<Product[]>([])
const preferences = ref<Preference[]>([])
const saving = ref(false)
const addressDialog = ref(false)
const passwordDialog = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const addressSaving = ref(false)
const editingAddressId = ref('')
const profileForm = reactive({ nickname: '', email: '', phone: '' })
const addressForm = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

const tabs = [
  { name: 'overview', label: '账户概览' },
  { name: 'address', label: '收货地址' },
  { name: 'favorites', label: '我的收藏' },
  { name: 'preferences', label: '导购偏好' },
  { name: 'history', label: '浏览足迹' }
]

const avatar = computed(() => (profile.value?.user.nickname || profile.value?.user.username || '智').slice(0, 1))

onMounted(load)

async function load() {
  loading.value = true
  try {
    profile.value = await userApi.profile()
    Object.assign(profileForm, {
      nickname: profile.value.user.nickname || '',
      email: profile.value.user.email || '',
      phone: profile.value.user.phone || ''
    })
    const [addressList, favoriteList, preferenceList, historyList] = await Promise.all([
      userApi.addresses(),
      userApi.favorites(),
      userApi.preferences(),
      userApi.history(8)
    ])
    addresses.value = addressList
    favorites.value = favoriteList
    preferences.value = preferenceList
    history.value = historyList
  } finally {
    loading.value = false
  }
}

function openPassword() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirm = ''
  passwordDialog.value = true
}

async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await userApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    passwordDialog.value = false
    auth.logout()
    router.push('/login')
  } finally {
    passwordSaving.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    await userApi.updateProfile(profileForm)
    await auth.refreshProfile()
    ElMessage.success('个人信息已更新')
    await load()
  } finally {
    saving.value = false
  }
}

function openAddress(address?: Address) {
  editingAddressId.value = address?.id || ''
  Object.assign(addressForm, {
    receiver: address?.receiver || '',
    phone: address?.phone || '',
    province: address?.province || '',
    city: address?.city || '',
    district: address?.district || '',
    detail: address?.detail || '',
    isDefault: address?.isDefault === 1
  })
  addressDialog.value = true
}

async function saveAddress() {
  if (!addressForm.receiver || !addressForm.phone || !addressForm.province || !addressForm.city || !addressForm.district || !addressForm.detail) {
    ElMessage.warning('请把地址填写完整')
    return
  }
  addressSaving.value = true
  try {
    if (editingAddressId.value) {
      await userApi.updateAddress(editingAddressId.value, addressForm)
    } else {
      await userApi.createAddress(addressForm)
    }
    addresses.value = await userApi.addresses()
    addressDialog.value = false
    ElMessage.success('收货地址已保存')
  } finally {
    addressSaving.value = false
  }
}

async function setDefaultAddress(id: string) {
  await userApi.setDefaultAddress(id)
  addresses.value = await userApi.addresses()
  ElMessage.success('已设为默认地址')
}

async function removeAddress(address: Address) {
  await ElMessageBox.confirm(`确定删除「${address.receiver}」的收货地址吗？`, '删除地址', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await userApi.deleteAddress(address.id)
  addresses.value = await userApi.addresses()
  ElMessage.success('已删除')
}

function addPreference() {
  if (preferences.value.length >= 10) {
    ElMessage.warning('最多设置 10 条偏好')
    return
  }
  preferences.value.push({ key: '品类', value: '', weight: 0.8 })
}

function removePreference(index: number) {
  preferences.value.splice(index, 1)
}

async function savePreferences() {
  const items = preferences.value.filter((item) => item.value.trim())
  saving.value = true
  try {
    preferences.value = await userApi.savePreferences(items)
    ElMessage.success('导购偏好已保存，下次对话会参考这些信息')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="profile-page">
    <div class="page-head">
      <div class="container">
        <span class="eyebrow">MY ACCOUNT</span>
        <h1>个人中心</h1>
        <p class="desc">管理收货地址、收藏与导购偏好。偏好会被 Agent 用于个性化推荐。</p>
      </div>
    </div>

    <div class="container profile-layout">
      <aside class="profile-aside">
        <div class="user-card">
          <div class="avatar-big">{{ avatar }}</div>
          <h3>{{ profile?.user.nickname || profile?.user.username }}</h3>
          <span class="username">@{{ profile?.user.username }}</span>
          <div class="user-stats">
            <div><strong>{{ profile?.orderCount ?? 0 }}</strong><span>订单</span></div>
            <div><strong>{{ profile?.favoriteCount ?? 0 }}</strong><span>收藏</span></div>
            <div><strong>{{ profile?.cartCount ?? 0 }}</strong><span>购物车</span></div>
          </div>
          <el-button round class="chat-btn" @click="router.push('/chat')">
            <el-icon><ChatDotRound /></el-icon> 找 Agent 聊聊
          </el-button>
        </div>

        <nav class="side-nav">
          <button v-for="tab in tabs" :key="tab.name" :class="{ active: active === tab.name }" @click="active = tab.name">
            {{ tab.label }}
          </button>
          <button @click="router.push('/orders')">我的订单 <el-icon><ArrowRight /></el-icon></button>
          <button v-if="auth.isAdmin" @click="router.push('/admin')">后台管理 <el-icon><ArrowRight /></el-icon></button>
        </nav>
      </aside>

      <section class="profile-main">
        <template v-if="active === 'overview'">
          <div class="panel">
            <div class="panel-head">
              <h3>账户信息</h3>
              <span class="muted">注册于 {{ profile?.user.createdAt?.slice(0, 10) || '—' }}</span>
            </div>
            <el-form label-position="top" class="profile-form">
              <div class="form-grid">
                <el-form-item label="用户名">
                  <el-input :model-value="profile?.user.username" disabled />
                </el-form-item>
                <el-form-item label="昵称">
                  <el-input v-model="profileForm.nickname" maxlength="30" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="profileForm.email" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input v-model="profileForm.phone" maxlength="20" />
                </el-form-item>
              </div>
              <div class="profile-actions">
                <el-button type="primary" round :loading="saving" @click="saveProfile">保存修改</el-button>
                <el-button round @click="openPassword">修改密码</el-button>
              </div>
            </el-form>
          </div>

          <div class="quick-grid">
            <button class="quick-card" @click="active = 'address'">
              <el-icon><Location /></el-icon>
              <div><strong>收货地址</strong><span>{{ addresses.length }} 个地址</span></div>
            </button>
            <button class="quick-card" @click="active = 'favorites'">
              <el-icon><Star /></el-icon>
              <div><strong>我的收藏</strong><span>{{ favorites.length }} 件商品</span></div>
            </button>
            <button class="quick-card" @click="active = 'preferences'">
              <el-icon><MagicStick /></el-icon>
              <div><strong>导购偏好</strong><span>{{ preferences.length }} 条偏好</span></div>
            </button>
            <button class="quick-card" @click="router.push('/orders')">
              <el-icon><Tickets /></el-icon>
              <div><strong>我的订单</strong><span>{{ profile?.orderCount ?? 0 }} 笔订单</span></div>
            </button>
          </div>

          <div v-if="history.length" class="panel">
            <div class="panel-head">
              <h3>最近浏览</h3>
              <button class="text-btn" @click="active = 'history'">查看全部</button>
            </div>
            <div class="mini-products">
              <router-link v-for="product in history.slice(0, 4)" :key="product.id" :to="`/product/${product.id}`" class="mini-product">
                <img :src="resolveAssetUrl(product.image)" :alt="product.name" />
                <span>{{ product.name }}</span>
              </router-link>
            </div>
          </div>
        </template>

        <template v-else-if="active === 'address'">
          <div class="panel">
            <div class="panel-head">
              <h3>收货地址</h3>
              <el-button round size="small" @click="openAddress()">+ 新增地址</el-button>
            </div>
            <div v-if="addresses.length" class="address-list">
              <article v-for="address in addresses" :key="address.id" class="address-item">
                <div class="address-icon"><el-icon><LocationFilled /></el-icon></div>
                <div class="address-body">
                  <div class="address-title">
                    <strong>{{ address.receiver }}</strong>
                    <span>{{ address.phone }}</span>
                    <span v-if="address.isDefault === 1" class="default-tag">默认</span>
                  </div>
                  <p>{{ address.province }}{{ address.city }}{{ address.district }}{{ address.detail }}</p>
                </div>
                <div class="address-actions">
                  <button v-if="address.isDefault !== 1" @click="setDefaultAddress(address.id)">设为默认</button>
                  <button @click="openAddress(address)">编辑</button>
                  <button class="danger" @click="removeAddress(address)">删除</button>
                </div>
              </article>
            </div>
            <div v-else class="empty">
              <strong>还没有收货地址</strong>
              <p>添加地址后即可下单。</p>
            </div>
          </div>
        </template>

        <template v-else-if="active === 'favorites'">
          <div class="panel">
            <div class="panel-head">
              <h3>我的收藏</h3>
              <span class="muted">{{ favorites.length }} 件</span>
            </div>
            <div v-if="favorites.length" class="fav-grid">
              <ProductCard v-for="product in favorites" :key="product.id" :product="product" compact />
            </div>
            <div v-else class="empty">
              <strong>还没有收藏商品</strong>
              <p>看到喜欢的商品点一下星标，就会出现在这里。</p>
            </div>
          </div>
        </template>

        <template v-else-if="active === 'preferences'">
          <div class="panel">
            <div class="panel-head">
              <div>
                <h3>导购偏好</h3>
                <p class="panel-desc">Agent 会在对话中参考这些偏好，也可以自动从对话中学习。</p>
              </div>
              <el-button round size="small" @click="addPreference">+ 添加偏好</el-button>
            </div>
            <div v-if="preferences.length" class="pref-list">
              <div v-for="(item, index) in preferences" :key="index" class="pref-row">
                <el-select v-model="item.key" style="width: 110px">
                  <el-option label="品类" value="品类" />
                  <el-option label="预算" value="预算" />
                  <el-option label="品牌" value="品牌" />
                  <el-option label="场景" value="场景" />
                  <el-option label="功能" value="功能" />
                </el-select>
                <el-input v-model="item.value" placeholder="例如：蓝牙耳机 / 300 / 跑步" />
                <div class="weight">
                  <span>权重</span>
                  <el-slider v-model="item.weight" :min="0.1" :max="1" :step="0.1" style="width: 100px" />
                  <b>{{ Number(item.weight).toFixed(1) }}</b>
                </div>
                <button class="remove-pref" @click="removePreference(index)"><el-icon><Close /></el-icon></button>
              </div>
            </div>
            <div v-else class="empty">
              <strong>还没有设置偏好</strong>
              <p>添加品类、预算或场景偏好，让推荐更懂你。</p>
            </div>
            <el-button type="primary" round style="margin-top: 18px" :loading="saving" @click="savePreferences">保存偏好</el-button>
          </div>
        </template>

        <template v-else>
          <div class="panel">
            <div class="panel-head">
              <h3>浏览足迹</h3>
              <span class="muted">最近 {{ history.length }} 件</span>
            </div>
            <div v-if="history.length" class="fav-grid">
              <ProductCard v-for="product in history" :key="product.id" :product="product" compact />
            </div>
            <div v-else class="empty">
              <strong>还没有浏览记录</strong>
              <p>去商品列表逛逛吧。</p>
            </div>
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="passwordDialog" title="修改密码" width="420px">
      <el-form label-position="top">
        <el-form-item label="原密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirm" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="savePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addressDialog" :title="editingAddressId ? '编辑地址' : '新增地址'" width="520px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="收货人"><el-input v-model="addressForm.receiver" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="addressForm.phone" maxlength="11" /></el-form-item>
          <el-form-item label="省份"><el-input v-model="addressForm.province" /></el-form-item>
          <el-form-item label="城市"><el-input v-model="addressForm.city" /></el-form-item>
        </div>
        <el-form-item label="区县"><el-input v-model="addressForm.district" /></el-form-item>
        <el-form-item label="详细地址"><el-input v-model="addressForm.detail" /></el-form-item>
        <el-checkbox v-model="addressForm.isDefault">设为默认地址</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="addressDialog = false">取消</el-button>
        <el-button type="primary" :loading="addressSaving" @click="saveAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  padding-bottom: 50px;
}

.profile-layout {
  display: grid;
  grid-template-columns: 250px 1fr;
  gap: 30px;
  padding-top: 30px;
  align-items: start;
}

.profile-aside {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.user-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 24px;
  text-align: center;
}

.avatar-big {
  width: 66px;
  height: 66px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-deep);
  display: grid;
  place-items: center;
  font-size: 25px;
  font-weight: 700;
  margin: 0 auto 12px;
}

.user-card h3 {
  font-size: 17px;
}

.username {
  font-size: 11px;
  color: var(--muted);
}

.user-stats {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin: 18px 0;
  padding: 14px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.user-stats div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-stats strong {
  font-size: 17px;
}

.user-stats span {
  font-size: 9px;
  color: var(--muted);
}

.chat-btn {
  width: 100%;
}

.side-nav {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 8px;
  display: flex;
  flex-direction: column;
}

.side-nav button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: none;
  background: transparent;
  text-align: left;
  padding: 11px 12px;
  border-radius: 9px;
  font-size: 12px;
  color: var(--ink-soft);
  cursor: pointer;
}

.side-nav button:hover {
  background: var(--bg);
}

.side-nav button.active {
  background: var(--ink);
  color: var(--on-ink);
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.panel-head h3 {
  font-size: 16px;
}

.panel-desc {
  font-size: 11px;
  color: var(--muted);
  margin-top: 5px;
}

.profile-form .form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.profile-actions {
  display: flex;
  gap: 8px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 13px;
  padding: 15px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-card:hover {
  border-color: var(--ink);
  transform: translateY(-2px);
}

.quick-card .el-icon {
  font-size: 19px;
  color: var(--brand);
}

.quick-card strong {
  display: block;
  font-size: 12px;
}

.quick-card span {
  font-size: 9px;
  color: var(--muted);
}

.mini-products {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.mini-product {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 10px;
  color: var(--muted);
}

.mini-product img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 9px;
  border: 1px solid var(--line);
}

.mini-product span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-btn {
  border: none;
  background: transparent;
  font-size: 11px;
  color: var(--brand);
  cursor: pointer;
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.address-item {
  display: flex;
  gap: 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 15px;
}

.address-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--bg);
  display: grid;
  place-items: center;
  color: var(--muted);
  flex-shrink: 0;
}

.address-body {
  flex: 1;
}

.address-title {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11px;
  color: var(--muted);
}

.address-title strong {
  font-size: 13px;
  color: var(--ink);
}

.default-tag {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 4px;
}

.address-body p {
  font-size: 11px;
  color: var(--muted);
  margin-top: 6px;
  line-height: 1.6;
}

.address-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.address-actions button {
  border: none;
  background: transparent;
  font-size: 10px;
  color: var(--muted);
  cursor: pointer;
}

.address-actions button:hover {
  color: var(--ink);
}

.address-actions .danger:hover {
  color: #d64545;
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.pref-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pref-row {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--line);
  border-radius: 11px;
  padding: 10px 12px;
}

.weight {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 10px;
  color: var(--muted);
  flex-shrink: 0;
}

.weight b {
  width: 24px;
  color: var(--ink);
}

.remove-pref {
  border: none;
  background: transparent;
  color: var(--muted-light);
  cursor: pointer;
  padding: 4px;
}

.remove-pref:hover {
  color: #d64545;
}

@media (max-width: 1000px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .profile-aside {
    position: static;
  }

  .user-stats {
    max-width: 300px;
    margin-left: auto;
    margin-right: auto;
  }

  .profile-actions {
  display: flex;
  gap: 8px;
}

.quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .fav-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .profile-form .form-grid,
  .quick-grid,
  .fav-grid {
    grid-template-columns: 1fr;
  }

  .pref-row {
    flex-wrap: wrap;
  }
}
</style>
