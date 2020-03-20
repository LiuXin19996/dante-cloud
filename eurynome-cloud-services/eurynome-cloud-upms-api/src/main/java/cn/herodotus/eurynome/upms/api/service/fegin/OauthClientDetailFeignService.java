package cn.herodotus.eurynome.upms.api.service.fegin;

import cn.herodotus.eurynome.component.common.domain.Result;
import cn.herodotus.eurynome.component.security.domain.HerodotusClientDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * <p> Description : OauthClientDetailFeignService </p>
 *
 * @author : gengwei.zheng
 * @date : 2020/3/19 18:10
 */
public interface OauthClientDetailFeignService {

    /**
     * 获取OauthClientDetail
     *
     * @param clientId OauthClientDetail ID
     * @return OauthClientDetail
     */
    @GetMapping("/oauth/client_details")
    Result<HerodotusClientDetails> findByClientId(@RequestParam(value = "clientId") String clientId);
}
