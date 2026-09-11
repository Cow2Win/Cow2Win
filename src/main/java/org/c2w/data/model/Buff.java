package org.c2w.data.model;


public sealed interface Buff permits RoleBuff, ElementBuff {
    BuffEffect effect();

    double bonusPercent();

    String display();
}
