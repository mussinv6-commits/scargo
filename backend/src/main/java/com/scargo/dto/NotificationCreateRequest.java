package com.scargo.dto;

import com.scargo.Enum.NotificationType; // [필요시] 임포트 확인
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor 
@Builder            
public class NotificationCreateRequest {

    private Long accountId;           
    private Long companyId;           
    private String title;             
    private String message;           
    private NotificationType notificationType; // [수정] String -> NotificationType Enum으로 변경
    private Long referenceId;         
}