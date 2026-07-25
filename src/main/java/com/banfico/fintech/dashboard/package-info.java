/**
 * Aggregated dashboard endpoint (Phase 4): DashboardController fans out per-account
 * balance/transaction calls concurrently (virtual threads / StructuredTaskScope) into
 * one shaped response for the frontend home screen.
 */
package com.banfico.fintech.dashboard;
