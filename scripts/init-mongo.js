var indexSpec = {
    "user":[
        {index:{email:1},option:{name:"email",unique:true,background:true}},
        {index:{"following.id":1},option:{name:"following",background:true}}
    ]
};

for (var colName in indexSpec)
{
    var col = db[colName];
    if(col.stats().ok != 1) {
        print("Create collection - " + colName);
        db.createCollection(colName);
        col = db[colName];
    }

    var indexInfo = db[colName].getIndexSpecs();
    for (var key in indexSpec[colName])
    {
        var spec = indexSpec[colName][key];
        var indexName = spec.option.name;
        var indexExists = false;
        for (var index in indexInfo)
        {
            if (indexInfo[index].name == indexName)
            {
                indexExists = true;
                break;
            }
        }
        if (!indexExists)
        {
            print("Create index:" + indexName + " for collection:" + colName);
            col.ensureIndex(spec.index, spec.option);
        }
    }
}
