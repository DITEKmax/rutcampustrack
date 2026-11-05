import { ref } from 'vue'
/** Auth-only preference; never writes the document or authenticated theme. */
export const authTheme = ref<'dark' | 'light'>('dark')
