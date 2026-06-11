private void onPlayerTick(PlayerTickEvent.Post event) {
    if (event.getEntity().level().isClientSide()) return;
    if (event.getEntity() instanceof ServerPlayer player) {
        ManaSyncManager.tick(player);
    }
}
