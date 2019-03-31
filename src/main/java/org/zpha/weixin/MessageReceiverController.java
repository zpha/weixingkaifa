package org.zpha.weixin;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/zph/weixin/receiver")
public class MessageReceiverController {
@GetMapping
public String echo( 
	String signature,
	String timestamp,
	String nonce,
	String echostr
	){
		
	
	return echostr;
}
 
}