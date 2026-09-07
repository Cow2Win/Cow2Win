package org.c2w.data.model;

import java.util.List;


public sealed interface Buff permits RoleBuff, ElementBuff {
    BuffEffect effect();

    double bonusPercent();

    List<String> buffProfits();

    String display();
}
