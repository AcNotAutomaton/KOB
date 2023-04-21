<template>
    <ContentField>
        <table class="table table-striped table-hover" style="text-align: center;">
            <thead>
                <tr>
                    <th>bot</th>
                    <th>对局次数</th>
                    <th>天梯分</th>
                    <th>作者</th>
                </tr>
            </thead>
            <tbody>
                <tr v-for="user in bots" :key="user.id">
                    <td>
                        <span class="record-user-username">{{ user.user_name }}</span>
                    </td>
                    <td>55 </td>
                    <td>{{ user.bot_rating }}</td>
                    <td>
                        <img @click="go_users(user.user_id)" :src="user.user_photo" alt="" class="record-user-photo">
                        &nbsp;
                    </td>
                </tr>
            </tbody>
        </table>
    </ContentField>
</template>

<script>
import ContentField from '../../components/ContentField.vue'
import { useStore } from 'vuex';
import router from "../../router/index"
import $ from 'jquery';
import { onMounted, ref } from 'vue';

export default {
    components: {
        ContentField
    },
    setup() {
        const store = useStore();
        let bots = ref([])
        const pull_bot =() => {
            $.ajax({
                url: "http://127.0.0.1:3000/api/user/bot/getlist/alluser/",
                type: "get",
                headers: {
                    Authorization: "Bearer " + store.state.user.token,
                },
                success(resp) {
                   bots.value = resp.bot
                   console.log(bots.value)
                },
                error(resp) {
                    console.log(resp);
                }
            })
        }

        onMounted(()=>{
            pull_bot()
        })

        const go_users = id =>{
            router.push(`/users/${id}/`)
        }


        return {
            bots,
            go_users
        }
    }
}
</script>

<style scoped>
img.record-user-photo {
    width: 4vh;
    border-radius: 50%;
    cursor: pointer;
}
</style>