package com.mainuddin.codereview.dto;

import java.util.List;

public record DashboardPageDTO<T>(List<T> items, long total, int page, int size) {}
