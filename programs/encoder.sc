__config() -> {
    'commands' -> {
        'set chests' -> 'set_chests',
        'set contents' -> 'set_contents',
        'remove' -> 'remove_chests',
        'reset contents' -> 'reset_contents',
        //'test' -> 'test',
    }
};


global_og_blocks = [];
global_location == null;
set_chests() -> (
    pos = player()~'pos';    
    [facing, offset] = get_offset();

    // get the starting position for both chest rows
    first_row = pos_offset(pos, facing, 1);
    second_row = first_row + 3*offset;
    global_location = [[first_row, second_row], facing, offset];

    // set the chest rows
    colors = ['red_concrete', 'green_concrete'];
    for(global_location:0,
        pos = _;

        //set marker block
        global_og_blocks:_i = block(pos_offset(pos, 'down', 1));
        set(pos_offset(pos, 'down', 1), colors:_i);
        //set chests
        loop(31,
            this_pos = pos_offset(pos, facing, _);
            set_double_chest(this_pos, facing, offset);
        );
    );
    reset_contents();
);


set_contents() -> (
    file_type = 'shared_text';

    files = list_files('item_layouts', file_type);
    
    // set the items
    empty_contents();
    for(files,
        item_line_file = _;
        
        items = read_file(item_line_file, file_type);
        for(items,
            item = _;
            number = _i;
            set_first_bit_group(item, item_line_file, number);
            set_second_bit_group(item, number);
        )
    );

    // set corrrect ss values
    [pos_pair, facing, offset] = check_positions();
    for(pos_pair,
        pos = _;
        loop(31,
            this_pos = pos_offset(pos, facing, _);
            correct_ss(this_pos);
        )
    );

    pprint('g', 'Set contents of chests');
);

reset_contents() -> (
    [pos_pair, facing, offset] = check_positions();
    for(pos_pair,
        pos = _;
        loop(31,
            this_pos = pos_offset(pos, facing, _);
            set_one_default_content(this_pos);
        );
    );

    pprint('g', 'Set contents of chests to dummy items')
);

empty_contents() -> (
    [pos_pair, facing, offset] = check_positions();
    for(pos_pair,
        pos = _;
        loop(31,
            this_pos = pos_offset(pos, facing, _);
            loop(54, inventory_set(this_pos, _, 0) );
        );
    );
);

set_one_default_content(pos) -> (
    loop(54,inventory_set(pos, _, 2, 'sea_pickle', '{display:{Name:\'{"text":"╚═Dummy═╝"}\'}}') );
    correct_ss(pos);
);

remove_chests() -> (
    [pos_pair, facing, offset] = check_positions();
    
    for(pos_pair,
        // replace chests with air
        pos = _;
        other_corner = pos_offset(pos, facing, 31) + offset;
        volume(pos, other_corner, set(_, 'air'));

        // replace marker block
        og_block = global_og_blocks:_i;
        set(pos(og_block), og_block);
    );
    global_location = null;
    global_og_blocks = [];

    pprint('g', 'Removed the chests')
);

check_positions() -> (
    if(global_location == null,
        pprint('r', 'You need to set the chests first');
        exit()
    );
    global_location
);

set_first_bit_group(item, item_line_file, number) -> (
    [pos_pair, facing, offset] = global_location;
    pos = pos_pair:0;

    number += 1;
    last_bit = str(floor(number / 2^5));
    row_bits = split('_', item_line_file):(-2);
    hall_bit =  split('_', item_line_file):(-1);
    code = row_bits + hall_bit + last_bit;

    // convert binary string to number
    inventory_number = reduce(split('', code), number(_) * 2^(4-_i) + _a, 0);
    chest_pos = pos_offset(pos, facing, inventory_number-1);

    if(inventory_number!=0,
        // find empty slot in chest
        slot = inventory_find(chest_pos, null);
        if(slot == null, pprint('#ff850a', str('Found full chest %d while encoding %s_%s_%sXXXXX: %s (first bitgroup: red)', inventory_number, row_bits, hall_bit, last_bit, item) ) );
        
        // set item
        inventory_set(chest_pos, slot, 2, item),
    //else
        //pprint('g', 'Found inventory with code 0-000-0XXXXX, skipping');
    );
);

set_second_bit_group(item, number) -> (
    [pos_pair, facing, offset] = global_location;
    pos = pos_pair:1;

    inventory_number = number % (2^5 - 1); // this is inventory indexing at 0, not 1 (unlike the reference file)
    chest_pos = pos_offset(pos, facing, inventory_number);

    // find empty slot in chest
    slot = inventory_find(chest_pos, null);
    if(slot == null, pprint('#ff850a', str('Found full chest %d while encoding XXX-X-%d: %s (second bitgroup: green)', inventory_number, number, item) ) );

    // set item
    inventory_set(chest_pos, slot, 2, item);
);

set_item_code(item, number) -> (
    [pos, facing, offset] = global_location;
    location2 = [pos_offset(pos, facing, 7), facing, offset];

    chest1 = number % 8;
    chest2 = (number - chest1) / 8;

    set_one_chest_item_code(global_location, chest1, item, number);
    set_one_chest_item_code(location2, chest2, item, number)
);

set_one_chest_item_code(location, chest_nr, item, number) -> (
    [pos, facing, offset] = location;

    if(chest_nr > 0,
        chest_nr += -1;
        chest_pos = pos_offset(pos, facing, chest_nr);
        slot = inventory_find(chest_pos, null);
        if(slot == null, pprint('#ff850a', str('Found full chest %s while encoding %d: %s', chest_nr, number, item) ) );
        inventory_set(chest_pos, slot, 2, item);
    );
);

set_item_hall(item, hall) -> (
    [pos, facing, offset] = global_location;

    hall = split('_', hall):(-1);
    hall_nr = global_hall_mapping:hall;

    if(hall_nr > 0,
        hall_nr += -1;
        chest_pos = pos_offset(pos, facing, hall_nr + 14);
        slot = inventory_find(chest_pos, null);
        if(slot == null, pprint('#ff850a', str('Found full chest %s while encoding %d: %s (%s)', hall_nr, number, item, hall) ) );
        inventory_set(chest_pos, slot, 2, item);
    );
);


correct_ss(pos) -> (
    target_ss = 2;
    first_empty = inventory_find(pos, null);
    //fill the slot with dummy items
    loop(54-first_empty, inventory_set(pos, first_empty+_, 2, 'sea_pickle', '{display:{Name:\'{"text":"╚═Dummy═╝"}\'}}') );

    //correct the ammpunt of items in the last few stacks to adjust the signal strength
    while(ss<target_ss, is = inventory_size(pos),
        //get the last slot
        slot = is-_-1;
        //get the item in the last slot
        item_tuple = inventory_get(pos, slot);

        //set items till the ss is right
        while(inventory_ss(pos) < target_ss, stack_limit(item_tuple:0)-item_tuple:1,
            inventory_set(pos, slot, item_tuple:1+1, item_tuple:0, item_tuple:2);
            item_tuple:1 += 1;
        )
    )
);

inventory_ss(pos) -> (
    inv_size = inventory_size(pos);
    items = map(range(inv_size), item = inventory_get(pos, _); if(item==null, continue()); [item:0, item:1]);
    r = floor(14/inv_size * reduce(items, 
        (_:1)/min(64, stack_limit(_:0)) + _a,
        0)+ min(1, length(items)));
);

set_double_chest(position, facing, offset) -> (
    if(!global_safe_set || (global_safe_set && air(position) && air(position+offset))   , 
        if(!global_safe_set, set(position, 'stone');, set(position+offset, 'stone'));
        set( position, 'chest', 'type', 'left', 'facing', facing);
        set( position + offset, 'chest', 'type', 'right', 'facing', facing)
    )
);

global_offset = {'south'->[-1, 0, 0], 'west'->[0,0, -1], 'north'->[1, 0, 0], 'east'->[0, 0, 1]};
get_offset() -> (
    facing = query( player(), 'facing');
    if( (global_offset ~ facing)== null, facing = query( player(), 'facing', 1));
    [facing, global_offset:facing];
);


pprint(fmt, msg) -> (
    if(fmt != null,
        msg = format(join(' ', fmt, msg))
    );
    print(player(), msg)
);
