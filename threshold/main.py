
li = [ 20, 30, 40, 50, 67, 79, 98 ]

cycle = 0

NUM_OF_SLOTS = 4

slot0 = 0
slot1 = 0
slot2 = 0
slot3 = 0


def compare( value, slot0_en=1, slot1_en=1, slot2_en=1, slot3_en=1 ):

    global slot0
    global slot1
    global slot2
    global slot3

    slot0 = 0
    slot1 = 0
    slot2 = 0
    slot3 = 0

    # These if-statements run parallel
    if ( slot0_en and value >= li[ cycle*NUM_OF_SLOTS ] ):
        slot0 = 1

    if ( slot1_en and value >= li[ cycle*NUM_OF_SLOTS + 1 ]):
        slot1 = 1

    if ( slot2_en and value >= li[ cycle*NUM_OF_SLOTS + 2 ]):
        slot2 = 1

    if ( slot3_en and value >= li[ cycle*NUM_OF_SLOTS + 3 ]):
        slot3 = 1


    if slot3 == 1:
        return True  # Repeat

    return False  # No repeat


def pop_count():
    return slot0 + slot1 + slot2 + slot3


def threshold( value ):
    global cycle
    slot3_en = 1

    while True:
        if ( cycle*NUM_OF_SLOTS + 3 == 7 ):
            slot3_en = 0

        if not compare( value, slot3_en = slot3_en ):
            break

        cycle += 1



    return cycle * NUM_OF_SLOTS + pop_count()


def reset():
    global slot0
    global slot1
    global slot2
    global slot3
    global cycle

    slot0 = 0
    slot1 = 0
    slot2 = 0
    slot3 = 0
    cycle = 0


if __name__ == "__main__":
    print(threshold(23));
    reset()
    print(threshold(33));
    reset()
    print(threshold(43));
    reset()
    print(threshold(53));
    reset()
    print(threshold(63));
    reset()
    print(threshold(73));
    reset()
    print(threshold(83));
    reset()
    print(threshold(93));
