/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020-2030 郑庚伟 ZHENGGENGWEI (码匠君), <herodotus@aliyun.com> Licensed under the AGPL License
 *
 * This file is part of Dante Cloud.
 *
 * Dante Cloud is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dante Cloud is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.herodotus.cn>.
 */

package org.dromara.dante.bpmn.logic.processor;

import org.dromara.dante.bpmn.logic.entity.ActIdUser;
import org.dromara.dante.bpmn.logic.service.ActIdUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * <p>Description: Debezium User 数据处理器 </p>
 *
 * @author : gengwei.zheng
 * @date : 2021/7/20 16:50
 */
@Component
public class ActIdUserProcessor extends AbstractProcessor<ActIdUser> {

    private static final Logger log = LoggerFactory.getLogger(ActIdUserProcessor.class);

    private final ActIdUserService actIdUserService;

    @Autowired
    public ActIdUserProcessor(ActIdUserService actIdUserService) {
        this.actIdUserService = actIdUserService;
    }

    @KafkaListener(topics = {"herodotus.public.sys_employee"}, groupId = "herodotus.debezium", containerFactory = "concurrentKafkaListenerContainerFactory")
    public void received(String body) {
        log.info("[Herodotus] |- Recived Debezium event message from [sys_employee], content is : [{}]", body);
        this.execute(body, ActIdUser.class);
    }

    @Override
    public void delete(ActIdUser entity) {
        actIdUserService.deleteById(entity.getId());
    }

    @Override
    public ActIdUser saveOrUpdate(ActIdUser entity) {
        return actIdUserService.saveAndFlush(entity);
    }
}
