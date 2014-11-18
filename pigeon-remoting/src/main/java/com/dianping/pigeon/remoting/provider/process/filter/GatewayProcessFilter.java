/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.provider.process.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.dianping.pigeon.config.ConfigChangeListener;
import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.RejectedException;
import com.dianping.pigeon.remoting.common.process.ServiceInvocationFilter;
import com.dianping.pigeon.remoting.common.process.ServiceInvocationHandler;
import com.dianping.pigeon.remoting.provider.domain.ProviderContext;
import com.dianping.pigeon.remoting.provider.process.statistics.AppStatisticsChecker;
import com.dianping.pigeon.remoting.provider.process.statistics.AppStatisticsHolder;
import com.dianping.pigeon.threadpool.DefaultThreadPool;
import com.dianping.pigeon.threadpool.ThreadPool;

/**
 * @author xiangwu
 * 
 */
public class GatewayProcessFilter implements ServiceInvocationFilter<ProviderContext> {

	private static final Logger logger = LoggerLoader.getLogger(GatewayProcessFilter.class);
	private static ConfigManager configManager = ExtensionLoader.getExtension(ConfigManager.class);
	private static boolean isAppLimitEnabled = configManager.getBooleanValue("pigeon.provider.applimit.enable", false);
	private static Map<String, Long> appLimitMap = new ConcurrentHashMap<String, Long>();
	private static ThreadPool statisticsCheckerPool = new DefaultThreadPool("Pigeon-Server-Statistics-Checker");

	static {
		String appLimitConfig = configManager.getStringValue("pigeon.provider.applimit");
		parseAppLimitConfig(appLimitConfig);
		ConfigManagerLoader.getConfigManager().registerConfigChangeListener(new InnerConfigChangeListener());
		AppStatisticsChecker appStatisticsChecker = new AppStatisticsChecker();
		statisticsCheckerPool.execute(appStatisticsChecker);
	}

	private static void parseAppLimitConfig(String appLimitConfig) {
		if (StringUtils.isNotBlank(appLimitConfig)) {
			try {
				String[] appLimitConfigPair = appLimitConfig.split(",");
				for (String str : appLimitConfigPair) {
					if (StringUtils.isNotBlank(str)) {
						String[] pair = str.split(":");
						if (pair != null && pair.length == 2) {
							appLimitMap.put(pair[0], Long.valueOf(pair[1]));
						}
					}
				}
			} catch (RuntimeException e) {
				logger.error("error while parsing app limit configuration:" + appLimitConfig, e);
			}
		}
	}

	@Override
	public InvocationResponse invoke(ServiceInvocationHandler handler, ProviderContext invocationContext)
			throws Throwable {
		if (logger.isDebugEnabled()) {
			logger.debug("invoke the GatewayProcessFilter, invocationContext:" + invocationContext);
		}
		InvocationRequest request = invocationContext.getRequest();
		String fromApp = request.getApp();
		InvocationResponse response = null;
		try {
			AppStatisticsHolder.flowIn(request);
			if (isAppLimitEnabled && StringUtils.isNotBlank(fromApp) && appLimitMap.containsKey(fromApp)) {
				Long limit = appLimitMap.get(fromApp);
				if (limit >= 0) {
					long requests = AppStatisticsHolder.getCapacityBucket(request).getCurrentRequests();
					if (requests + 1 > limit) {
						throw new RejectedException("request from app:" + fromApp
								+ " refused, max requests limit reached:" + limit);
					}
				}
			}
			response = handler.handle(invocationContext);
			return response;
		} finally {
			AppStatisticsHolder.flowOut(request);
		}
	}

	private static class InnerConfigChangeListener implements ConfigChangeListener {

		@Override
		public void onKeyUpdated(String key, String value) {
			if (key.endsWith("pigeon.provider.applimit")) {
				parseAppLimitConfig(value);
			} else if (key.endsWith("pigeon.provider.applimit.enable")) {
				try {
					isAppLimitEnabled = Boolean.valueOf(value);
				} catch (RuntimeException e) {
				}
			}
		}

		@Override
		public void onKeyAdded(String key, String value) {

		}

		@Override
		public void onKeyRemoved(String key) {

		}

	}
}
