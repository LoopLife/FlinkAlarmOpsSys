package com.ktvmi.flinkconfig.Controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.annotation.NacosConfigListener;
import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.util.List;
import java.util.Properties;

import com.ktvmi.flinkconfig.EntityClass.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP;

@RestController
@RequestMapping({"/nacosconfig"})
public class NacosController {
    private  String config="";
    /**
     * 获取配置信息
     */
    @NacosValue(value = "${useLocalCache:false}",autoRefreshed = true)
    private boolean useLocalCache;//运行时可被动态更新了。

    @NacosInjected
    private ConfigService configService;//注册默认服务

    @Deprecated//待改进
    @GetMapping("/getalarm")
    public String getalarm(String id)throws NacosException{
        String content = configService.getConfig("FlinkJob."+id+".properties.alarm", "BaseService", 5000);
        NamingService naming = NamingFactory.createNamingService(System.getProperty("serveAddr"));
        return content;
    }

    /**
     * 发布配置:创建和修改配置时使用的同一个发布接口，当配置不存在时会创建配置，当配置已存在时会更新配置。
     * @param alarmRule 报警规则类
     * @return ResponseMsg 统一返回信息
     * @throws NacosException
     */
    @PostMapping("/publichalarm")
    public ResponseMsg publishAlarm(@Valid @RequestBody AlarmRule alarmRule)throws NacosException{
        try {
            configService.publishConfig("FlinkJob."+alarmRule.getJobid()+"."+alarmRule.getRuleid(),"AlarmRules", JSON.toJSONString(alarmRule));
        }
        catch (NacosException ex){
            ResponseMsg badmsg = new ResponseMsg("Catch Exception"+ex.getMessage(), 233);
            return badmsg;
        }
        ResponseMsg goodmsg = new ResponseMsg("Success", 0);
        return goodmsg;
    }
    @PostMapping("/deleteAlarm")
    public ResponseMsg removeAlarm(@Valid @RequestBody AlarmRule alarmRule)throws NacosException{
        try {
            System.out.println("Remove:FlinkJob."+alarmRule.getJobid()+"."+alarmRule.getRuleid());
            configService.removeConfig("FlinkJob."+alarmRule.getJobid()+"."+alarmRule.getRuleid(),"AlarmRules");
        }
        catch (NacosException ex){
            ResponseMsg badmsg = new ResponseMsg("Catch Exception"+ex.getMessage(), 233);
            return badmsg;
        }
        ResponseMsg goodmsg = new ResponseMsg("Delete Success", 0);
        return goodmsg;
    }
    @RequestMapping(value = {"config/get"},method = {RequestMethod.GET})
    @ResponseBody
    public boolean get() {
        return this.useLocalCache;
    }
@PostMapping("/updatealarmrule")
public ResponseMsg updateAlarmRule(@Valid @RequestBody AlarmRuleMap alarmRuleMap)throws NacosException{
        try {
            String oldAlarmId="FlinkJob."+alarmRuleMap.getJobid()+"."+alarmRuleMap.getOldruleid();
            String config=configService.getConfig(oldAlarmId,"AlarmRules",3000);//获取时间窗口和计数值
            AlarmRule alarmRule=JSON.parseObject(config,AlarmRule.class);//实例化旧数据
            alarmRule.setRuleid(alarmRuleMap.getNewruleid());//新数据覆盖
            alarmRule.setRulecontent(alarmRuleMap.getRulecontent());
            configService.publishConfig("FlinkJob."+alarmRule.getJobid()+"."+alarmRule.getRuleid(),"AlarmRules", JSON.toJSONString(alarmRule));//新发布
            configService.removeConfig(oldAlarmId,"AlarmRules");//旧的删除
        }
        catch (NacosException ex){
            ResponseMsg badmsg = new ResponseMsg("Catch Exception"+ex.getMessage(), 233);
            return badmsg;
        }
    ResponseMsg goodmsg = new ResponseMsg("Delete Success", 0);
    return goodmsg;
}


    @RequestMapping(value = {"config/publish"},method = {RequestMethod.GET})
    @ResponseBody
    public boolean publish() throws NacosException {
        configService.publishConfig("example2", "DEFAULT_GROUP", "9527");
        return true;
    }



    @NacosConfigListener(dataId = "example2")
    public void onMessage(String config){

        this.config=config;
        System.out.println("NacosConfigListener:"+this.config);
    }
    /**
     * 启动服务发现
     */
    @NacosInjected//依赖注入
    private NamingService namingService;//注入 Nacos 的 NamingService 实例
    @RequestMapping(
            value = {"discovery/get"},
            method = {RequestMethod.GET}
    )
    @ResponseBody
    public List<Instance> get(@RequestParam String serviceName) throws NacosException {
        return this.namingService.getAllInstances(serviceName);
    }
}
