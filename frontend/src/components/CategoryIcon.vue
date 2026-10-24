<script setup lang="ts">
/**
 * 品类单色线性图标：颜色跟随 currentColor，选中态由父级设置为品牌色。
 * 传入 categoryId 或名称自动匹配，未命中时回退到通用图标。
 */
import { computed } from 'vue'

const props = defineProps<{ categoryId?: string; name?: string; size?: number }>()

type IconKey =
  | 'digital'
  | 'computer'
  | 'phone'
  | 'watch'
  | 'headphone'
  | 'keyboard'
  | 'laptop'
  | 'sport'
  | 'running'
  | 'yoga'
  | 'beauty'
  | 'skincare'
  | 'home'
  | 'coffee'
  | 'purifier'
  | 'smarthome'
  | 'robot'
  | 'food'
  | 'beans'
  | 'bag'
  | 'backpack'
  | 'generic'

const KEYWORDS: Array<[IconKey, string[]]> = [
  ['headphone', ['headphone', '耳机', '耳機']],
  ['phone', ['phone', '手机', '手機']],
  ['watch', ['watch', '手表', '腕表']],
  ['keyboard', ['keyboard', '键盘', '鍵盤']],
  ['laptop', ['laptop', '笔记本', '筆記本']],
  ['computer', ['computer', '办公', '電腦', '电脑']],
  ['digital', ['digital', '数码', '數碼', '影音']],
  ['running', ['running', '跑鞋', '跑步']],
  ['yoga', ['yoga', '瑜伽']],
  ['sport', ['sport', '运动', '運動', '户外']],
  ['skincare', ['skincare', '护肤', '護膚']],
  ['beauty', ['beauty', '美妆', '美妝', '彩妆']],
  ['coffee', ['coffee', '咖啡机', '咖啡機']],
  ['beans', ['beans', '咖啡豆']],
  ['purifier', ['purifier', '净化器', '淨化', '空气']],
  ['robot', ['robot', '扫地', '掃地', '机器人']],
  ['smarthome', ['smarthome', '智能家居', '智能家']],
  ['home', ['home', '家用', '家电', '家居']],
  ['food', ['food', '食品', '零食', '食材']],
  ['backpack', ['backpack', '双肩', '雙肩', '背包']],
  ['bag', ['bag', '箱包', '包袋']],
  ['generic', []]
]

const key = computed<IconKey>(() => {
  const haystack = `${props.categoryId || ''} ${props.name || ''}`.toLowerCase()
  for (const [iconKey, words] of KEYWORDS) {
    if (words.some((word) => haystack.includes(word.toLowerCase()))) {
      return iconKey
    }
  }
  return 'generic'
})

/** 每条 path 为 24x24 视图下的线性图标 */
const PATHS: Record<IconKey, string[]> = {
  headphone: ['M4 14v-2a8 8 0 0 1 16 0v2', 'M4 14h3v5H5a1 1 0 0 1-1-1v-4Z', 'M20 14h-3v5h2a1 1 0 0 0 1-1v-4Z'],
  phone: ['M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z', 'M10 18h4'],
  watch: ['M9 3h6l1 4H8l1-4Z', 'M8 7h8a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z', 'M9 21h6l1-4H8l1 4Z', 'M12 10v3l2 1'],
  keyboard: ['M3 7h18a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Z', 'M6 10h.01M9 10h.01M12 10h.01M15 10h.01M18 10h.01M7 14h10'],
  laptop: ['M5 5h14a1 1 0 0 1 1 1v9H4V6a1 1 0 0 1 1-1Z', 'M3 18h18', 'M9 18l.5 2h5l.5-2'],
  computer: ['M3 5h18v10H3z', 'M8 19h8', 'M12 15v4'],
  digital: ['M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z', 'M9 20h6', 'M12 16v4', 'M8 9l2 2-2 2M13 13h3'],
  running: ['M3 17c2 0 3-1 5-1s2 1 4 1 3-1 5-1 3 1 4 1', 'M8 13l2-3 3 1 2-3', 'M15 5.5a1.5 1.5 0 1 0 0-.01'],
  yoga: ['M12 4a1.6 1.6 0 1 0 0-.01', 'M12 7v5', 'M12 12l-4 2M12 12l4 2', 'M8 20l4-6 4 6'],
  sport: ['M4 12h16', 'M7 8v8M17 8v8', 'M4 10v4M20 10v4'],
  skincare: ['M9 8h6a1 1 0 0 1 1 1v10a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V9a1 1 0 0 1 1-1Z', 'M10 8V6h4v2', 'M12 3v3', 'M12 13v4'],
  beauty: ['M12 3c2.5 3 4 5 4 7a4 4 0 0 1-8 0c0-2 1.5-4 4-7Z', 'M6 20h12'],
  coffee: ['M5 8h11v6a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4V8Z', 'M16 9h2a2 2 0 0 1 0 4h-2', 'M4 21h14', 'M8 4v2M11 3v2'],
  beans: ['M6 18c-2-4 0-10 6-12', 'M18 6c2 4 0 10-6 12', 'M9 9l6 6', 'M15 9l-6 6'],
  purifier: ['M6 4h12a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Z', 'M9 8h6M9 12h6M9 16h4', 'M18 9h3M18 15h3'],
  smarthome: [
    'M4 11 L12 4 L20 11',
    'M6 10v10h12V10',
    'M12 13.6a2.4 2.4 0 0 1 2.4 2.4c0 1.6-2.4 3.4-2.4 3.4S9.6 17.6 9.6 16a2.4 2.4 0 0 1 2.4-2.4Z'
  ],
  robot: ['M5 10h14a1 1 0 0 1 1 1v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a1 1 0 0 1 1-1Z', 'M12 6v4', 'M12 4a1.4 1.4 0 1 0 0-.01', 'M9 14h.01M15 14h.01', 'M9 17h6'],
  home: ['M4 11 L12 4 L20 11', 'M6 10v10h12V10', 'M10 20v-5h4v5'],
  food: ['M5 3v7a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2V3', 'M8 12v9', 'M16 3c-1.5 2-2 4-2 6s.5 3 2 3 2-1 2-3-.5-4-2-6Z', 'M16 12v9'],
  bag: ['M5 8h14l1 12H4L5 8Z', 'M9 8V6a3 3 0 0 1 6 0v2'],
  backpack: ['M7 7a5 5 0 0 1 10 0v13H7V7Z', 'M9 7V5h6v2', 'M7 13h10', 'M10 17h4'],
  generic: ['M4 5h16v14H4z', 'M4 9h16', 'M9 5v14']
}
</script>

<template>
  <svg
    class="category-icon"
    :width="size || 22"
    :height="size || 22"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, index) in PATHS[key]" :key="index" :d="d" />
  </svg>
</template>

<style scoped>
.category-icon {
  display: block;
  flex-shrink: 0;
}
</style>