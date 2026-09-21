<template>
  <div>
    <WeatherCalendar />
    <PlateCheckMap />
    <router-view />
  </div>
</template>
<script setup>
import WeatherCalendar from '../components/WeatherCalendar.vue'
import PlateCheckMap from '../components/PlateCheckMap.vue'
</script>
<script>
export default {
  name: "Main",
};
</script>