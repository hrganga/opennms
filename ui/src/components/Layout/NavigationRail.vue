<template>
  <FeatherNavigationRail @update:modelValue="onNavRailClick">
    <template v-slot:main>
      <FeatherRailItem
        :class="{ selected: isSelected('/') }"
        href="#/"
        :icon="Instances"
        title="Nodes"
      />
      <FeatherRailItem
        :class="{ selected: isSelected('/map') }"
        href="#/map"
        :icon="Location"
        title="Map"
      />
      <FeatherRailItem
        :class="{ selected: isSelected('/file-editor') }"
        v-if="isAdmin"
        href="#/file-editor"
        :icon="AddNote"
        title="File Editor"
      />
      <FeatherRailItem
        :class="{ selected: isSelected('/logs') }"
        v-if="isAdmin"
        href="#/logs"
        :icon="MarkComplete"
        title="Logs"
      />
      <FeatherRailItem
        :class="{ selected: isSelected('/open-api') }"
        href="#/open-api"
        :icon="Cloud"
        title="Endpoints"
      />
    </template>
  </FeatherNavigationRail>
</template>
<script setup lang=ts>
import { computed } from 'vue'
import { useStore } from 'vuex'
import { useRoute } from 'vue-router'
import Instances from "@featherds/icon/hardware/Instances"
import AddNote from "@featherds/icon/action/AddNote"
import Location from "@featherds/icon/action/Location"
import MarkComplete from "@featherds/icon/action/MarkComplete"
import Cloud from "@featherds/icon/action/Cloud"
import {
  FeatherNavigationRail,
  FeatherRailItem,
} from "@featherds/navigation-rail"

const store = useStore()
const route = useRoute()
const isAdmin = computed(() => store.getters['authModule/isAdmin'])
const navRailOpen = computed(() => store.state.appModule.navRailOpen)
const onNavRailClick = () => store.dispatch('appModule/setNavRailOpen', !navRailOpen.value)
const isSelected = (path: string) => path === route.fullPath
</script>

<style scopes lang="scss">
.nav-header {
  display: none !important;
}
</style>
