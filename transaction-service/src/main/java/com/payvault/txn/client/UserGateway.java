package com.payvault.txn.client;

import com.payvault.txn.dto.TxnDtos.UserDto;
import com.payvault.txn.exception.ApiException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserGateway {

    private final UserClient client;

    public UserGateway(UserClient client) {
        this.client = client;
    }

    public UserDto get(Long id, String notFoundMessage) {
        try {
            return client.get(id);
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
        } catch (FeignException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "User Service unavailable, please retry");
        }
    }
}
