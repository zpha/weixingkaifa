package org.zpha.weixin.controller;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;

import javax.xml.bind.JAXB;

import org.zpha.weixin.domain.InMessage;
import org.zpha.weixin.service.MessageTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/zph/weixin/receiver")
public class MessageReceiverController {

	private static final Logger LOG = LoggerFactory.getLogger(MessageReceiverController.class);

	@Autowired
	@Qualifier("inMessageTemplate")
	private RedisTemplate<String, InMessage> inMessageTemplate;

	@GetMapping // 只处理GET请求
	public String echo(
			@RequestParam("signature") String signature, 
			@RequestParam("timestamp") String timestamp, 
			@RequestParam("nonce") String nonce, 
			@RequestParam("echostr") String echostr
	) {
		// 正常来讲，需要把timestamp和nonce放入一个数组，并进行排序
		// 接着把排序后的两个元素拼接成一个新的String
		// 使用SHA-1算法对新的String进行加密
		// 最后把加密的结果跟signature进行比较，如果相同表示验证通过，返回echostr

		// 原路返回echostr的值，返回以后微信公众号平台就能够认为：服务器对接成功
		return echostr;
	}

	// 当微信客户端发送任意消息给公众号的时候，消息都会通过POST方式提交到当前类里面。
	// @PostMapping专门用于处理POST请求。
	// 消息的格式是XML形式的字符串，整个消息放入了请求体里面。
	@PostMapping
	public String onMessage(@RequestParam("signature") String signature, //
			@RequestParam("timestamp") String timestamp, //
			@RequestParam("nonce") String nonce, //
			@RequestBody String xml) {
		LOG.debug("收到用户发送给公众号的信息: \n-----------------------------------------\n"
				+ "{}\n-----------------------------------------\n", xml);

//		if (xml.contains("<MsgType><![CDATA[text]]></MsgType>")) {
//		} else if (xml.contains("<MsgType><![CDATA[image]]></MsgType>")) {
//		} else if (xml.contains("<MsgType><![CDATA[voice]]></MsgType>")) {
//		} else if (xml.contains("<MsgType><![CDATA[video]]></MsgType>")) {
//		} else if (xml.contains("<MsgType><![CDATA[location]]></MsgType>")) {
//		}

		// 截取消息类型
		// <MsgType><![CDATA[text]]></MsgType>
		String type = xml.substring(xml.indexOf("<MsgType><![CDATA[") + 18);
		type = type.substring(0, type.indexOf("]]></MsgType>"));

		Class<InMessage> cla = MessageTypeMapper.getClass(type);

		// 使用JAXB完成XML转换为Java对象的操作
		InMessage inMessage = JAXB.unmarshal(new StringReader(xml), cla);

		LOG.debug("转换得到的消息对象 \n{}\n", inMessage.toString());

		// 把消息放入消息队列
		inMessageTemplate.execute(new RedisCallback<String>() {

			// connection对象表示跟Redis数据库的连接
			@Override
			public String doInRedis(RedisConnection connection) throws DataAccessException {
				try {
					// 发布消息的时候，需要准备两个byte[]
					// 一个作为通道名称来使用，类似于无线电广播，不同的频道声音是隔离的。通道名称是Redis用来隔离不同数据的。
					// 比如文本消息、图片消息处理方式不同，所以使用前缀来隔离： text* 表示文本消息、image* 表示图片消息。
					// 建议在多人共享一个服务器的时候，每个人使用不同的数据库实例即可，并且建议在通道名称之前加上反向代理的前缀。

					String channel = "zpha_" + inMessage.getMsgType();

					// 消息内容要自己序列化才能放入队列中
					ByteArrayOutputStream out = new ByteArrayOutputStream();// 输出流
					ObjectOutputStream oos = new ObjectOutputStream(out);
					oos.writeObject(inMessage);

					Long l = connection.publish(channel.getBytes(), out.toByteArray());
					System.out.println("发布结果：" + l);
				} catch (Exception e) {
					LOG.error("把消息放入队列时出现问题：" + e.getLocalizedMessage(), e);
				}
				return null;
			}
		});

		// 由于后面会把消息放入队列中，所以这里直接返回success。
		return "success";
	}
}
