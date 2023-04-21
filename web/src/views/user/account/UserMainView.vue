<template>
    <div class="container">
        <div class="row">
            <div class="col-3">
                <div class="card" style="margin-top: 20px;">
                    <div class="card-body">
                        <img :src="user.photo" alt="" style="width: 100%;">
                    </div>
                </div>
            </div>
            <div class="col-9">
                <div class="card" style="margin-top: 20px;">
                    <div class="card-header">
                        <span style="font-size: 130%">
                            {{ user.username }}
                            <div style="font-size: 18px;float:right;">
                                <span @click="reflshAll" style="cursor: pointer;" class="badge rounded-pill text-bg-primary">
                                    重新加载
                                </span>
                                <span  class="badge rounded-pill text-bg-info">
                                    Bot名称
                                </span>
                            </div>
                        </span>
                    </div>
                    <div class="card-body">
                        <h4>Ta的bot</h4>
                        <table class="table table-striped table-hover" v-if="haveText">
                            <thead>
                                <tr>
                                    <th>名称</th>
                                    <th>创建时间</th>
                                    <th>使用次数</th>
                                    <th>积分</th>

                                </tr>
                            </thead>
                            <tbody>
                                <tr v-for="bot in bots" :key="bot.id">
                                    <td>{{ bot.title }}</td>
                                    <td>{{ bot.createtime }}</td>
                                    <td>26</td>
                                    <td>{{ bot.rating}}</td>

                                </tr>
                            </tbody>
                        </table>
                        <div v-else>
                            <span class="badge text-bg-secondary">纯手玩</span>
                            <span class="badge text-bg-light">纯手玩</span>
                            <span class="badge text-bg-dark">纯手玩</span>
                        </div>
                        <h4>Ta的战绩</h4> 
                        <div class="ttt">
                            <table  class="table table-striped table-hover " style="text-align: center;" > 
                                <thead>
                                    <tr>
                                        <th>A</th>
                                        <th>B</th>
                                        <th>对战结果</th>
                                        <th>对战时间</th>
                                        <th>步数</th>
                                        <th>操作</th>
                                    </tr>
                                </thead>
                                <tbody >
                                    <tr v-for="record in records" :key="record.record.id">
                                        <td>
                                            <!-- <img  :src="record.a_photo" alt="" class="record-user-photo"> -->
                                            &nbsp;
                                            <span class="record-user-username">
                                                {{ record.a_username }}
                                                <span class="badge  text-bg-info">
                                                    {{ record.a_bot_title }}
                                                </span>
                                            </span>
                                        </td>
                                        <td>
                                            <!-- <img  :src="record.b_photo" alt="" class="record-user-photo"> -->
                                            &nbsp;
                                            <span class="record-user-username">
                                                {{ record.b_username }}
                                                <span class="badge  text-bg-info">
                                                    {{ record.b_bot_title }}
                                                </span>
                                            </span>
                                        </td>
                                        <td v-if="record.result==='胜利'">
                                            <span class="badge text-bg-primary">
                                                {{ record.result }}
                                            </span>
                                        </td>
                                        <td v-else-if="record.result==='失败'">
                                            <span class="badge text-bg-danger">
                                                {{ record.result }}
                                            </span>
                                        </td>
                                        <td v-else>
                                            <span class="badge text-bg-secondary">
                                            {{ record.result }}
                                            </span>
                                        </td>
                                        <td>{{ record.record.createtime }}</td>
                                        <td>{{ record.record.asteps.length }} </td>
                                        <td>
                                            <button @click="open_record_content(record.record.id)" type="button"
                                                class="btn btn-secondary">查看录像</button>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                    </div>
                </div>
            </div>
        </div>
    </div>
    
</template>

<script>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router';
import $ from 'jquery'
import { useStore } from 'vuex'
import router from '../../../router/index'


export default{
    setup(){
        const route = useRoute()
        const store = useStore()
        let bots = ref([])
        let user_id = ref(0)
        let user = ref({})
        let records = ref([])
        let haveText = ref(true)
        const get_user_info = () => {
            $.ajax({
                url: "http://127.0.0.1:3000/api/user/account/user/info/",
                type: "get",
                data:{
                    id: user_id.value
                },
                headers: {
                    Authorization: "Bearer " + store.state.user.token,
                },
                success(resp) {
                    user.value = resp
                    console.log(user.value)
                }
            })
        }

        const refresh_bots = () => {
            $.ajax({
                url: "http://127.0.0.1:3000/api/user/bot/getlist/user/",
                type: "get",
                data:{
                    user_id: user_id.value
                },
                headers: {
                    Authorization: "Bearer " + store.state.user.token,
                },
                success(resp) {
                    bots.value = resp;
                    if(bots.value.length===0)haveText.value = false
                    else haveText.value = true
                    console.log(resp)
                }
            })
        }

        const get_list = ()=>{
            $.ajax({
                url: "http://127.0.0.1:3000/api/record/getuesrlist/",
                data: {
                    id: user_id.value
                },
                type: "get",
                headers: {
                    Authorization: "Bearer " + store.state.user.token,
                },
                success(resp) {
                    records.value = resp.records;
                    console.log(resp)
                },
                error(resp) {
                    console.log(resp);
                }
            })
        }

        const stringTo2D = map => {
            let g = [];
            for (let i = 0, k = 0; i < 13; i++) {
                let line = [];
                for (let j = 0; j < 14; j++, k++) {
                    if (map[k] === '0') line.push(0);
                    else line.push(1);
                }
                g.push(line);
            }
            return g;
        }

        const open_record_content = recordId => {
            for (const record of records.value) {
                if (record.record.id === recordId) {
                    store.commit("updateIsRecord", true);
                    store.commit("updateGame", {
                        map: stringTo2D(record.record.map),
                        a_id: record.record.aid,
                        a_sx: record.record.asx,
                        a_sy: record.record.asy,
                        b_id: record.record.bid,
                        b_sx: record.record.bsx,
                        b_sy: record.record.bsy,
                    });
                    store.commit("updateSteps", {
                        a_steps: record.record.asteps,
                        b_steps: record.record.bsteps,
                    });
                    store.commit("updateProgress", 0)
                    store.commit("updateRecordLoser", record.record.loser);
                    router.push({
                        name: "record_content",
                        params: {
                            recordId
                        }
                    })
                    break;
                }
            }
        }

        const reflshAll = ()=>{
            get_user_info()
            refresh_bots()
            get_list()
        }

        onMounted(()=>{

            user_id.value = route.params.id
            console.log(user_id.value)
            get_user_info()
            refresh_bots()
            get_list()
        })

        refresh_bots();

        return{
            user,
            bots,
            haveText,
            records,
            reflshAll,
            open_record_content
        }
}


}
</script>

<style scoped>
img.record-user-photo {
    width: 4vh;
    border-radius: 50%;
}
.ttt{
    height: 500px;
    overflow: auto;
}
</style>
