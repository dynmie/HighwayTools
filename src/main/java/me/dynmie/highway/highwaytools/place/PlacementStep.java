package me.dynmie.highway.highwaytools.place;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record PlacementStep(BlockPos supportPos, Direction side, Vec3 hitVec, BlockPos placedPos) {
