package com.Atom2Universe.app.crypto.clicker

/** Natural fur colors: outline, shaded fur, light fur and eye. Selected once per clicker view. */
internal enum class RabbitCoat(
    private val outline: Int,
    private val shade: Int,
    private val fur: Int,
    private val eye: Int = 0xFF241C18.toInt()
) {
    WHITE(0xFF746A63.toInt(), 0xFFD8CEC4.toInt(), 0xFFFFFAF0.toInt()),
    GREY(0xFF494746.toInt(), 0xFF93908D.toInt(), 0xFFD2CFCA.toInt()),
    SAND(0xFF79604A.toInt(), 0xFFC5A47C.toInt(), 0xFFF0DCB5.toInt()),
    FAWN(0xFF684B35.toInt(), 0xFFB38557.toInt(), 0xFFDEB782.toInt()),
    BROWN(0xFF44312B.toInt(), 0xFF89634D.toInt(), 0xFFBB9474.toInt()),
    CHARCOAL(0xFF292727.toInt(), 0xFF615A55.toInt(), 0xFF8E857D.toInt());

    fun animation() = ClickerAnimalArt.rabbit.recolored(mapOf(
        0xFF636363.toInt() to outline,
        0xFFA0CDF0.toInt() to shade,
        0xFFFFFFFF.toInt() to fur,
        0xFF2234D1.toInt() to eye
    ))
}
