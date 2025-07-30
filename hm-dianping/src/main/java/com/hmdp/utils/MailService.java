package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setSubject("验证码通知");
        message.setText("您的验证码是：" + code + "，有效期5分钟，请及时使用！");
        message.setTo(to);
        message.setFrom(fromEmail); // 发件人邮箱
        mailSender.send(message);
    }
}
