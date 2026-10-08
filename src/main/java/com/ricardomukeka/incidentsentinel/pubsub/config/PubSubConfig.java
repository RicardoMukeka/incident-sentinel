package com.ricardomukeka.incidentsentinel.pubsub.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;

/**
 * Par canal principal (AckMode.MANUAL, ack/nack manual em NovoErroSub) +
 * canal DLT (AckMode.AUTO, só persiste falha em NovoErroDLTSub) — molde
 * exato de service-checkout/pubsub/config/PubSubConfig.java (docs/ARCHITECTURE.md).
 */
@Configuration
public class PubSubConfig {

	@Value("${spring.cloud.gcp.pubsub.incidentsentinel.novo-erro.subscription}")
	private String novoErroSubscriptionName;

	@Value("${spring.cloud.gcp.pubsub.incidentsentinel.novo-erro.dlt.subscription}")
	private String novoErroDLTSubscriptionName;

	@Bean
	public MessageChannel novoErroChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel novoErroDLTChannel() {
		return new DirectChannel();
	}

	@Bean
	public PubSubInboundChannelAdapter novoErroChannelAdapter(
			@Qualifier("novoErroChannel") MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {

		PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
				novoErroSubscriptionName);
		adapter.setOutputChannel(inputChannel);
		adapter.setAckMode(AckMode.MANUAL);
		adapter.setPayloadType(String.class);

		return adapter;
	}

	@Bean
	public PubSubInboundChannelAdapter novoErroChannelDLTAdapter(
			@Qualifier("novoErroDLTChannel") MessageChannel inputChannel, PubSubTemplate pubSubTemplate) {

		PubSubInboundChannelAdapter adapter = new PubSubInboundChannelAdapter(pubSubTemplate,
				novoErroDLTSubscriptionName);
		adapter.setOutputChannel(inputChannel);
		adapter.setAckMode(AckMode.AUTO);
		adapter.setPayloadType(String.class);

		return adapter;
	}
}
