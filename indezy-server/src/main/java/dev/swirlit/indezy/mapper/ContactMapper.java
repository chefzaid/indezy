package dev.swirlit.indezy.mapper;

import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.model.Contact;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ContactMapper {

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.companyName")
    @Mapping(target = "freelanceId", source = "freelance.id")
    @Mapping(target = "fullName", expression = "java(contact.getFirstName() + (contact.getLastName() != null ? \" \" + contact.getLastName() : \"\"))")
    ContactDto toDto(Contact contact);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Contact toEntity(ContactDto contactDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ContactDto contactDto, @MappingTarget Contact contact);
}
